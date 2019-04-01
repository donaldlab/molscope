package edu.duke.cs.molscope.render

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.imgui.context
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.window.Monitors
import cuchaz.kludge.window.Size
import cuchaz.kludge.window.Window
import cuchaz.kludge.window.Windows


class WindowRenderer(
	title: String = "MolScope",
	width: Int = 800,
	height: Int = 600,
	var backgroundColor: ColorRGBA = ColorRGBA.Float(0.3f, 0.3f, 0.3f)
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

	init {

		// init the window manager
		Windows.init()
		Windows.autoClose()

		// check for vulkan support from the window manager
		if (!Windows.isVulkanSupported) {
			throw Error("No Vulkan support from window manager")
		}

		// listen to problems from the window manager
		Windows.errors.setOut(System.err)
	}

	// TODO: we can make all this private, right?

	// make a window and show it
	// TODO: allow resizing the window
	val win =
		Window(
			size = Size(width, height),
			title = title
		)
		.autoClose()
		.apply {
			centerOn(Monitors.primary)
			visible = true
		}

	val renderer = Renderer(
		vulkanExtensions = Windows.requiredVulkanExtensions
	).autoClose()

	// make a surface for the window
	val surface = renderer.vulkan.surface(win).autoClose()

	// create the device and the queues
	val graphicsFamily = renderer.physicalDevice.findQueueFamily(IntFlags.of(PhysicalDevice.QueueFamily.Flags.Graphics))
	val surfaceFamily = renderer.physicalDevice.findQueueFamily(surface)
	val device = renderer.physicalDevice.device(
		queuePriorities = mapOf(
			graphicsFamily to listOf(1.0f),
			surfaceFamily to listOf(1.0f)
		),
		features = renderer.deviceFeatures,
		extensionNames = setOf(PhysicalDevice.SwapchainExtension)
	).autoClose()
	val graphicsQueue = device.queues[graphicsFamily]!![0]
	val surfaceQueue = device.queues[surfaceFamily]!![0]

	// build the swapchain
	val swapchain = renderer.physicalDevice.swapchainSupport(surface).run {
		swapchain(
			device,
			surfaceFormat = find(Image.Format.B8G8R8A8_UNORM, Image.ColorSpace.SRGB_NONLINEAR)
				?: surfaceFormats.first().also { println("using fallback surface format: $it") },
			presentMode = find(PresentMode.Mailbox)
				?: find(PresentMode.FifoRelaxed)
				?: find(PresentMode.Fifo)
				?: presentModes.first().also { println("using fallback present mode: $it") }
		)
	}.autoClose()

	// make the render pass
	val colorAttachment =
		Attachment(
			format = swapchain.surfaceFormat.format,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			finalLayout = Image.Layout.PresentSrc
		)
	val subpass =
		Subpass(
			pipelineBindPoint = PipelineBindPoint.Graphics,
			colorAttachments = listOf(
				colorAttachment to Image.Layout.ColorAttachmentOptimal
			)
		)
	val renderPass = device
		.renderPass(
			attachments = listOf(colorAttachment),
			subpasses = listOf(subpass),
			subpassDependencies = listOf(
				SubpassDependency(
					src = Subpass.External.dependency(
						stage = IntFlags.of(PipelineStage.ColorAttachmentOutput)
					),
					dst = subpass.dependency(
						stage = IntFlags.of(PipelineStage.ColorAttachmentOutput),
						access = IntFlags.of(Access.ColorAttachmentRead, Access.ColorAttachmentWrite)
					)
				)
			)
		).autoClose()

	// make one framebuffer for each swapchain image in the render pass
	val framebuffers = swapchain.images
		.map { image ->
			device.framebuffer(
				renderPass,
				imageViews = listOf(
					image.view().autoClose()
				),
				extent = swapchain.extent
			).autoClose()
		}

	// make a graphics command buffer for each framebuffer
	val commandPool = device
		.commandPool(
			graphicsFamily,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	val commandBuffers = framebuffers.map { commandPool.buffer() }

	// make the descriptor pool
	// 1000 of each is probably enough, right?
	val descriptorPool = device.descriptorPool(
		maxSets = 1000,
		sizes = DescriptorType.Counts(
			DescriptorType.values().map { type -> type to 1000 }
		)
	).autoClose()

	// make semaphores for command buffer synchronization
	val imageAvailable = device.semaphore().autoClose()
	val renderFinished = device.semaphore().autoClose()

	init {
		// init ImGUI
		Imgui.load().autoClose()
		println("ImGUI loaded, version ${Imgui.version}")
		Imgui.context().autoClose()
		Imgui.init(win, graphicsQueue, descriptorPool, renderPass)
		Imgui.initFonts()
	}


	/** main render loop, does not return until the window is closed */
	fun renderLoop(blockRender: CommandBuffer.() -> Unit, blockGui: Commands.() -> Unit) {

		while (!win.shouldClose()) {

			Windows.pollEvents()

			// get the next frame info
			val imageIndex = swapchain.acquireNextImage(imageAvailable)
			val framebuffer = framebuffers[imageIndex]
			val commandBuffer = commandBuffers[imageIndex]

			// define the gui for this frame
			Imgui.frame {
				blockGui()
			}

			// record the command buffer every frame
			commandBuffer.apply {
				begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

				blockRender()

				// draw the GUI in one render pass
				beginRenderPass(
					renderPass,
					framebuffer,
					swapchain.rect,
					clearValues = mapOf(
						colorAttachment to backgroundColor.toClearColor()
					)
				)
				Imgui.draw(this)
				endRenderPass()
				end()
			}

			// render the frame
			graphicsQueue.submit(
				commandBuffers[imageIndex],
				waitFor = listOf(Queue.WaitInfo(imageAvailable, IntFlags.of(PipelineStage.ColorAttachmentOutput))),
				signalTo = listOf(renderFinished)
			)
			surfaceQueue.present(
				swapchain,
				imageIndex,
				waitFor = renderFinished
			)
			surfaceQueue.waitForIdle()
		}

		// wait for the device to finish before returning
		device.waitForIdle()
	}
}
