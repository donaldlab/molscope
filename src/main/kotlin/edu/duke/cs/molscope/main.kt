package edu.duke.cs.molscope

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.imgui.context
import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.window.Monitors
import cuchaz.kludge.window.Size
import cuchaz.kludge.window.Window
import cuchaz.kludge.window.Windows
import java.nio.file.Paths


fun main() = autoCloser {

	// init the window manager
	Windows.init()
	Windows.autoClose()

	// check for vulkan support from the window manager
	if (!Windows.isVulkanSupported) {
		throw Error("No Vulkan support from window manager")
	}

	// listen to problems from the window manager
	Windows.errors.setOut(System.err)

	// make the main vulkan instance with the extensions we need
	val vulkan = Vulkan(
		extensionNames = Windows.requiredVulkanExtensions + setOf(Vulkan.DebugExtension),
		layerNames = setOf(Vulkan.StandardValidationLayer)
	).autoClose()

	// listen to problems from vulkan
	vulkan.debugMessenger(
		severities = IntFlags.of(DebugMessenger.Severity.Error, DebugMessenger.Severity.Warning)
	) { severity, type, msg ->
		println("VULKAN: ${severity.toFlagsString()} ${type.toFlagsString()} $msg")
	}.autoClose()

	// make a window
	val win = Window(
		size = Size(640, 480),
		title = "MolScope"
	).autoClose()
	win.centerOn(Monitors.primary)
	win.visible = true

	// make a surface for the window
	val surface = vulkan.surface(win).autoClose()

	// pick a physical device: prefer discrete GPU
	val physicalDevice = vulkan.physicalDevices
		.asSequence()
		.sortedBy { if (it.properties.type == PhysicalDevice.Type.DiscreteGpu) 0 else 1 }
		.first()

	// create the device and the queues
	val graphicsFamily = physicalDevice.findQueueFamily(IntFlags.of(PhysicalDevice.QueueFamily.Flags.Graphics))
	val surfaceFamily = physicalDevice.findQueueFamily(surface)
	val device = physicalDevice.device(
		queuePriorities = mapOf(
			graphicsFamily to listOf(1.0f),
			surfaceFamily to listOf(1.0f)
		),
		features = PhysicalDevice.Features().apply {
			geometryShader = true
			fragmentStoresAndAtomics = true
		},
		extensionNames = setOf(PhysicalDevice.SwapchainExtension)
	).autoClose()
	val graphicsQueue = device.queues[graphicsFamily]!![0]
	val surfaceQueue = device.queues[surfaceFamily]!![0]

	// build the swapchain
	val swapchain = physicalDevice.swapchainSupport(surface).run {
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

	// allocate a depth buffer on the GPU
	val depthBuffer = device.
		image(
			type = Image.Type.TwoD,
			extent = swapchain.extent.to3D(1),
			format = Image.Format.R32_UINT,
			usage = IntFlags.of(Image.Usage.TransferDst, Image.Usage.Storage)
		)
		.autoClose()
		.allocate { memType ->
			memType.flags.hasAll(IntFlags.of(
				MemoryType.Flags.DeviceLocal
			))
		}
		.autoClose()
	val depthBufferView = depthBuffer.image.view().autoClose()

	// make a uniform buf for the view transformations
	// TODO: optimize memory usage for this buffer? Or use push constants?
	val viewBuf = device
		.buffer(
			size = Float.SIZE_BYTES.toLong(),
			usage = IntFlags.of(Buffer.Usage.UniformBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocate { memType ->
			memType.flags.hasAll(IntFlags.of(
				MemoryType.Flags.HostVisible,
				MemoryType.Flags.HostCoherent
			))
		}
		.autoClose()

	// make the descriptor pool
	// 1000 of each is probably enough, right?
	val descriptorPool = device.descriptorPool(
		maxSets = 1000,
		sizes = DescriptorType.Counts(
			DescriptorType.values().map { type -> type to 1000 }
		)
	).autoClose()

	// build the descriptor set layout
	val depthBufBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Fragment)
	)
	val viewBufBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.UniformBuffer,
		stages = IntFlags.of(ShaderStage.Vertex)
	)
	val descriptorSetLayout = device.descriptorSetLayout(listOf(
		depthBufBinding, viewBufBinding
	)).autoClose()

	// make a descriptor set for each framebuffer
	val descriptorSets = descriptorPool.allocate(
		framebuffers.map { descriptorSetLayout }
	)

	// update all the descriptor sets
	device.updateDescriptorSets(
		writes = descriptorSets.flatMap { set ->
			listOf(
				set.address(depthBufBinding).write(
					images = listOf(
						DescriptorSet.ImageInfo(
							view = depthBufferView,
							layout = Image.Layout.General
						)
					)
				),
				set.address(viewBufBinding).write(
					buffers = listOf(
						DescriptorSet.BufferInfo(viewBuf.buffer)
					)
				)
			)
		}
	)

	// make the graphics pipeline
	val graphicsPipeline = device.graphicsPipeline(
		renderPass,
		stages = listOf(
			device.shaderModule(Paths.get("build/shaders/shader.vert.spv"))
				.autoClose()
				.stage("main", ShaderStage.Vertex),
			device.shaderModule(Paths.get("build/shaders/shader.geom.spv"))
				.autoClose()
				.stage("main", ShaderStage.Geometry),
			device.shaderModule(Paths.get("build/shaders/shader.frag.spv"))
				.autoClose()
				.stage("main", ShaderStage.Fragment)
		),
		descriptorSetLayouts = listOf(descriptorSetLayout),
		vertexInput = VertexInput {
			binding(stride = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4) {
				attribute(
					location = 0,
					format = Image.Format.R32G32B32_SFLOAT,
					offset = 0
				)
				attribute(
					location = 1,
					format = Image.Format.R32_SFLOAT,
					offset = Float.SIZE_BYTES*3
				)
				attribute(
					location = 2,
					format = Image.Format.R8G8B8A8_UNORM,
					offset = Float.SIZE_BYTES*4
				)
			}
		},
		inputAssembly = InputAssembly(InputAssembly.Topology.PointList),
		rasterizationState = RasterizationState(
			cullMode = IntFlags.of(CullMode.Back),
			frontFace = FrontFace.Counterclockwise
		),
		viewports = listOf(swapchain.viewport),
		scissors = listOf(swapchain.rect),
		attachmentBlends = listOf(
			colorAttachment to ColorBlendState.Attachment(
				color = ColorBlendState.Attachment.Part(
					src = BlendFactor.SrcAlpha,
					dst = BlendFactor.OneMinusSrcAlpha,
					op = BlendOp.Add
				),
				alpha = ColorBlendState.Attachment.Part(
					src = BlendFactor.One,
					dst = BlendFactor.Zero,
					op = BlendOp.Add
				)
			)
		)
	).autoClose()

	// allocate the vertex buffer on the GPU
	val vertexBuf = device.
		buffer(
			size = graphicsPipeline.vertexInput.size*12,
			usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocate { memType ->
			memType.flags.hasAll(IntFlags.of(
				MemoryType.Flags.DeviceLocal
			))
		}
		.autoClose()

	// upload geometry to the vertex buffer
	autoCloser {
		graphicsQueue.submit(commandPool.buffer().apply {
			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			// allocate a staging buffer and write vertex data to it
			val stagingBuf = device
				.buffer(
					vertexBuf.buffer.size,
					IntFlags.of(Buffer.Usage.TransferSrc)
				)
				.autoClose()
				.allocate { memType ->
					memType.flags.hasAll(IntFlags.of(
						MemoryType.Flags.HostVisible,
						MemoryType.Flags.HostCoherent
					))
				}
				.autoClose()
				.apply {
					memory.map { buf ->

						fun putAtom(x: Float, y: Float, z: Float, element: Element) {

							buf.putFloat(x)
							buf.putFloat(y)
							buf.putFloat(z)
							buf.putFloat(element.radius)
							buf.putColor4Bytes(element.color)
						}

						// an N-terminal alanine
						putAtom(14.699f, 27.060f, 24.044f, Element.Nitrogen)
						putAtom(15.468f, 27.028f, 24.699f, Element.Hydrogen)
						putAtom(15.072f, 27.114f, 23.102f, Element.Hydrogen)
						putAtom(14.136f, 27.880f, 24.237f, Element.Hydrogen)
						putAtom(13.870f, 25.845f, 24.199f, Element.Carbon)
						putAtom(14.468f, 24.972f, 23.937f, Element.Hydrogen)
						putAtom(13.449f, 25.694f, 25.672f, Element.Carbon)
						putAtom(12.892f, 24.768f, 25.807f, Element.Hydrogen)
						putAtom(14.334f, 25.662f, 26.307f, Element.Hydrogen)
						putAtom(12.825f, 26.532f, 25.978f, Element.Hydrogen)
						putAtom(12.685f, 25.887f, 23.222f, Element.Carbon)
						putAtom(11.551f, 25.649f, 23.607f, Element.Oxygen)

						buf.flip()
					}
				}

			copyBuffer(stagingBuf.buffer, vertexBuf.buffer)

			end()
		})
		graphicsQueue.waitForIdle()
	}

	// init ImGUI
	Imgui.load().autoClose()
	println("ImGUI loaded, version ${Imgui.version}")
	Imgui.context().autoClose()
	Imgui.init(win, graphicsQueue, descriptorPool, renderPass)
	Imgui.initFonts()

	// make semaphores for command buffer synchronization
	val imageAvailable = device.semaphore().autoClose()
	val renderFinished = device.semaphore().autoClose()

	// GUI state
	val winOpen = Ref.of(true)
	val check = Ref.of(false)
	var counter = 0

	// render state
	val startTime = System.currentTimeMillis()

	// main loop
	while (!win.shouldClose()) {

		Windows.pollEvents()

		// define the gui for this frame
		Imgui.frame {

			if (winOpen.value) {
				setNextWindowSize(400f, 200f)
				begin("Dear ImGUI", winOpen, IntFlags.of(Commands.BeginFlags.NoResize))

				text("This is a GUI!")

				if (checkbox("Yes?", check)) {
					println("checkbox: ${check.value}")
				}
				sameLine()
				text("Check is ${if (check.value) "checked" else "not checked"}")

				if (button("Increment!", width=200f, height=60f)) {
					counter++
				}
				sameLine()
				text("clicked $counter times")

				text("display size: ${Imgui.io.displaySize.width} x ${Imgui.io.displaySize.height}")
				text("frame time: ${String.format("%.3f", 1000f*Imgui.io.deltaTime)} ms")
				text("FPS: ${String.format("%.3f", Imgui.io.frameRate)}")

				end()
			}
		}

		// get the next frame info
		val imageIndex = swapchain.acquireNextImage(imageAvailable)
		val framebuffer = framebuffers[imageIndex]
		val commandBuffer = commandBuffers[imageIndex]

		// update the view buffer
		viewBuf.memory.map { buf ->
			val seconds = (System.currentTimeMillis() - startTime).toFloat()/1000
			buf.putFloat(Math.PI.toFloat()/3f*seconds)
			buf.flip()
		}

		// record the command buffer every frame
		commandBuffer.apply {

			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			// clear the depth buffer to the max depth
			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.TopOfPipe),
				dstStage = IntFlags.of(PipelineStage.FragmentShader),
				images = listOf(
					depthBuffer.image.barrier(
						dstAccess = IntFlags.of(Access.ShaderRead, Access.ShaderWrite),
						newLayout = Image.Layout.General
					)
				)
			)
			clearImage(
				depthBuffer.image,
				Image.Layout.General,
				ClearValue.Color.Int(-1, 0, 0, 0) // -1 is the signed int that maps to the max unsigned int
			)

			beginRenderPass(
				renderPass,
				framebuffer,
				swapchain.rect,
				clearValues = mapOf(
					colorAttachment to ClearValue.Color.Float(0.0f, 0.0f, 0.0f)
				)
			)

			// draw geometry
			bindPipeline(graphicsPipeline)
			bindDescriptorSet(descriptorSets[imageIndex], graphicsPipeline)
			bindVertexBuffer(vertexBuf.buffer)
			draw(vertices = 12)

			// draw the GUI
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

	// wait for the device to finish before cleaning up
	device.waitForIdle()

} // end of scope here cleans up all autoClose() resources


enum class Element(val symbol: String, val radius: Float, val color: ColorRGBA) {

	Hydrogen("H", 1f, ColorRGBA.Int(200, 200, 200)),
	Carbon("C", 1.75f, ColorRGBA.Int(60, 60, 60)),
	Nitrogen("N", 1.55f, ColorRGBA.Int(20, 20, 200)),
	Oxygen("O", 1.4f, ColorRGBA.Int(200, 20, 20));

	companion object {

		operator fun get(symbol: String) =
			values()
				.find { it.symbol == symbol }
				?: throw NoSuchElementException("unknown element: $symbol")
				// haha, the class name literally means element this time!
	}
}
