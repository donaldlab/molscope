package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.SIZE_BYTES
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.Element
import java.nio.file.Paths


class SlideRenderer(
	val device: Device,
	val graphicsFamily: PhysicalDevice.QueueFamily,
	val width: Int,
	val height: Int,
	var backgroundColor: ColorRGBA = ColorRGBA.Float(0f, 0f, 0f)
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

	val graphicsQueue = device.queues[graphicsFamily]!![0]
	val extent = Extent2D(width, height)
	val rect = Rect2D(Offset2D(0, 0), extent)

	// make the render pass
	val colorAttachment =
		Attachment(
			format = Image.Format.R8G8B8A8_UNORM,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			finalLayout = Image.Layout.ShaderReadOnlyOptimal
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

	// make the render image
	val image = device
		.image(
			Image.Type.TwoD,
			extent.to3D(1),
			colorAttachment.format,
			IntFlags.of(Image.Usage.ColorAttachment, Image.Usage.Sampled)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val imageView = image.image.view().autoClose()
	val imageSampler = device.sampler().autoClose()

	// make a framebuffer
	val framebuffer = device
		.framebuffer(
			renderPass,
			imageViews = listOf(imageView),
			extent = extent
		)
		.autoClose()

	// allocate a depth buffer on the GPU
	val depthBuffer = device.
		image(
			type = Image.Type.TwoD,
			extent = extent.to3D(1),
			format = Image.Format.R32_UINT,
			usage = IntFlags.of(Image.Usage.TransferDst, Image.Usage.Storage)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val depthBufferView = depthBuffer.image.view().autoClose()

	// make a uniform buf for the view transformations
	val viewBuf = device
		.buffer(
			size = 6*Float.SIZE_BYTES.toLong(),
			usage = IntFlags.of(Buffer.Usage.UniformBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	// make the descriptor pool
	val descriptorPool = device.descriptorPool(
		maxSets = 1,
		sizes = DescriptorType.Counts(
			DescriptorType.StorageImage to 1,
			DescriptorType.UniformBuffer to 1
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
		stages = IntFlags.of(ShaderStage.Vertex, ShaderStage.Geometry, ShaderStage.Fragment)
	)
	val descriptorSetLayout = device.descriptorSetLayout(listOf(
		depthBufBinding, viewBufBinding
	)).autoClose()

	// make the descriptor set
	val descriptorSet = descriptorPool.allocate(listOf(descriptorSetLayout))[0]

	// make a graphics command buffer
	val commandPool = device
		.commandPool(
			graphicsFamily,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	val commandBuffer = commandPool.buffer()

	init {
		// update the descriptor set
		device.updateDescriptorSets(
			writes = listOf(
				descriptorSet.address(depthBufBinding).write(
					images = listOf(
						DescriptorSet.ImageInfo(
							view = depthBufferView,
							layout = Image.Layout.General
						)
					)
				),
				descriptorSet.address(viewBufBinding).write(
					buffers = listOf(
						DescriptorSet.BufferInfo(viewBuf.buffer)
					)
				)
			)
		)
	}

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
		viewports = listOf(Viewport(
			0.0f,
			0.0f,
			extent.width.toFloat(),
			extent.height.toFloat()
		)),
		scissors = listOf(rect),
		attachmentBlends = listOf(
			colorAttachment to ColorBlendState.Attachment(
				color = ColorBlendState.Attachment.Part(
					src = BlendFactor.SrcAlpha,
					dst = BlendFactor.OneMinusSrcAlpha,
					op = BlendOp.Add
				),
				alpha = ColorBlendState.Attachment.Part(
					src = BlendFactor.One,
					dst = BlendFactor.One,
					op = BlendOp.Max
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
		.allocateDevice()
		.autoClose()
		.apply {

			// upload geometry to the vertex buffer
			transferHtoD { buf ->

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

	private val startTime = System.currentTimeMillis()

	fun render(renderFinished: Semaphore? = null) {

		// update the view buffer
		viewBuf.transferHtoD { buf ->
			val seconds = (System.currentTimeMillis() - startTime).toFloat()/1000
			buf.putFloat(Math.PI.toFloat()/3f*seconds)
			buf.putFloat(-20f + 4f) // z near, in camera space
			buf.putFloat(-20f - 4f) // z far
			buf.putFloat(40f) // magnification
			buf.putFloat(320f) // win x
			buf.putFloat(240f) // win y
			buf.flip()
		}

		// record the command buffer
		commandBuffer.apply {
			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			// transition images before render
			// https://www.khronos.org/registry/vulkan/specs/1.1-extensions/html/vkspec.html#synchronization-access-types-supported
			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.TopOfPipe),
				dstStage = IntFlags.of(PipelineStage.ColorAttachmentOutput),
				images = listOf(
					image.image.barrier(
						dstAccess = IntFlags.of(Access.ColorAttachmentWrite),
						newLayout = Image.Layout.ColorAttachmentOptimal
					)
				)
			)
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

			// clear the depth buffer to the max depth
			clearImage(
				depthBuffer.image,
				Image.Layout.General,
				ClearValue.Color.Int(-1, 0, 0, 0) // -1 is the signed int that maps to the max unsigned int
			)

			beginRenderPass(
				renderPass,
				framebuffer,
				rect,
				clearValues = mapOf(
					colorAttachment to backgroundColor.toClearColor()
				)
			)

			// draw geometry
			bindPipeline(graphicsPipeline)
			bindDescriptorSet(descriptorSet, graphicsPipeline)
			bindVertexBuffer(vertexBuf.buffer)
			draw(vertices = 12)

			endRenderPass()
			end()
		}

		// render the frame
		graphicsQueue.submit(
			commandBuffer,
			waitFor = listOf(),
			signalTo =
				if (renderFinished != null) {
					listOf(renderFinished)
				} else {
					emptyList()
				}
		)
	}
}
