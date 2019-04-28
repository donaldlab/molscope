package edu.duke.cs.molscope.render

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.imgui.context
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.window.Window
import cuchaz.kludge.window.Windows


internal class WindowRenderer(
	val win: Window,
	var backgroundColor: ColorRGBA = ColorRGBA.Float(0.3f, 0.3f, 0.3f)
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

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

	init {
		// init ImGUI
		Imgui.load().autoClose()
		Imgui.context().autoClose()
		Imgui.init(win, graphicsQueue, descriptorPool, renderPass)
		Imgui.initFonts()
	}

	// make semaphores for command buffer synchronization
	private val imageAvailable = device.semaphore().autoClose()
	private val renderFinished = device.semaphore().autoClose()

	fun render(waitFor: List<Semaphore>? = null, blockGui: Commands.() -> Unit) {

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

		// what are we waiting for?
		val waitSemaphores = mutableListOf(Queue.WaitInfo(imageAvailable, IntFlags.of(PipelineStage.ColorAttachmentOutput)))
		if (waitFor != null) {
			for (semaphore in waitFor) {
				waitSemaphores.add(Queue.WaitInfo(semaphore, IntFlags.of(PipelineStage.ColorAttachmentOutput)))
			}
		}

		// render the frame
		graphicsQueue.submit(
			commandBuffers[imageIndex],
			waitFor = waitSemaphores,
			signalTo = listOf(renderFinished)
		)
		surfaceQueue.present(
			swapchain,
			imageIndex,
			waitFor = renderFinished
		)
		surfaceQueue.waitForIdle()
	}

	fun waitForIdle() = device.waitForIdle()
}
