package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.vulkan.Queue
import edu.duke.cs.molscope.CameraCommand
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.view.SpaceFilling
import org.joml.Vector3f


internal class SlideRenderer(
	val queue: Queue,
	val width: Int,
	val height: Int,
	oldRenderer: SlideRenderer? = null,
	var backgroundColor: ColorRGBA = ColorRGBA.Float(0f, 0f, 0f)
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	override fun close() = closer.close()

	val device get() = queue.device

	val extent = Extent2D(width, height)
	val rect = Rect2D(Offset2D(0, 0), extent)

	// make the render pass
	val colorAttachment =
		Attachment(
			format = Image.Format.R8G8B8A8_UNORM,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			// finish this on ShaderReadOnlyOptimal, so WindowRenderer can sample it
			finalLayout = Image.Layout.ShaderReadOnlyOptimal
		)
	val depthAttachment =
		Attachment(
			format = Image.Format.D32_SFLOAT,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			finalLayout = Image.Layout.DepthStencilAttachmentOptimal
		)
	// TODO: output attachments for object picking?
	val subpass =
		Subpass(
			pipelineBindPoint = PipelineBindPoint.Graphics,
			colorAttachments = listOf(
				colorAttachment to Image.Layout.ColorAttachmentOptimal
			),
			depthStencilAttachment = depthAttachment to Image.Layout.DepthStencilAttachmentOptimal
		)
	val renderPass = device
		.renderPass(
			attachments = listOf(colorAttachment, depthAttachment),
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

	// make the depth buffer
	val depth = device
		.image(
			Image.Type.TwoD,
			extent.to3D(1),
			depthAttachment.format,
			IntFlags.of(Image.Usage.DepthStencilAttachment),
			tiling = Image.Tiling.Optimal // need "optimal" tiling for depth buffers
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val depthView = depth.image.view(
		range = Image.SubresourceRange(aspectMask = IntFlags.of(Image.Aspect.Depth))
	).autoClose()

	// make a framebuffer
	val framebuffer = device
		.framebuffer(
			renderPass,
			imageViews = listOf(imageView, depthView),
			extent = extent
		)
		.autoClose()

	// make a camera
	val camera: Camera = Camera(device)
		.autoClose()
		.apply {

			// if we have an old renderer, copy the camera state
			if (oldRenderer != null) {

				set(oldRenderer.camera)
				resize(width, height)
			}
		}

	// make the descriptor pool
	val descriptorPool = device.descriptorPool(
		maxSets = 1,
		sizes = DescriptorType.Counts(
			DescriptorType.UniformBuffer to 1
		)
	).autoClose()

	// build the descriptor set layout
	val viewBufBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.UniformBuffer,
		stages = IntFlags.of(ShaderStage.Vertex, ShaderStage.Geometry, ShaderStage.Fragment)
	)
	val descriptorSetLayout = device.descriptorSetLayout(listOf(
		viewBufBinding
	)).autoClose()

	// make the descriptor set
	val descriptorSet = descriptorPool.allocate(listOf(descriptorSetLayout))[0]

	// make a graphics command buffer
	val commandPool = device
		.commandPool(
			queue.family,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	val commandBuffer = commandPool.buffer()

	init {
		// update the descriptor set
		device.updateDescriptorSets(
			writes = listOf(
				descriptorSet.address(viewBufBinding).write(
					buffers = listOf(
						DescriptorSet.BufferInfo(camera.buf.buffer)
					)
				)
			)
		)
	}

	fun graphicsPipeline(
		stages: List<ShaderModule.Stage>,
		vertexInput: VertexInput,
		inputAssembly: InputAssembly
	) = device.graphicsPipeline(
		renderPass,
		stages,
		descriptorSetLayouts = listOf(descriptorSetLayout),
		vertexInput = vertexInput,
		inputAssembly = inputAssembly,
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
		colorAttachmentBlends = mapOf(
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
		),
		depthStencilState = DepthStencilState()
	)

	private val sphereRenderer = SphereRenderer(this).autoClose()

	fun render(slide: Slide.Locked, renderFinished: Semaphore? = null) {

		// update the renderers with views
		sphereRenderer.update(slide.views.filterIsInstance<SpaceFilling>())

		// update the camera
		while (slide.camera.queue.isNotEmpty()) {
			val cmd = slide.camera.queue.pollFirst() ?: break
			when (cmd) {
				is CameraCommand.LookAtBox -> camera.lookAtBox(
						width, height,
						focalLength = 200f,
						look = Vector3f(0f, 0f, 1f),
						up = Vector3f(0f, 1f, 0f),
						box = cmd.aabb
					)
			}
		}
		camera.upload()

		// record the command buffer
		commandBuffer.apply {
			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			// get the framebuffer attachments ready for rendering
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
				dstStage = IntFlags.of(PipelineStage.EarlyFragmentTests),
				images = listOf(
					depth.image.barrier(
						dstAccess = IntFlags.of(Access.DepthStencilAttachmentRead, Access.DepthStencilAttachmentWrite),
						newLayout = Image.Layout.DepthStencilAttachmentOptimal,
						range = Image.SubresourceRange(aspectMask = IntFlags.of(Image.Aspect.Depth))
					)
				)
			)

			beginRenderPass(
				renderPass,
				framebuffer,
				rect,
				clearValues = mapOf(
					colorAttachment to backgroundColor.toClearColor(),
					depthAttachment to ClearValue.DepthStencil(depth = 1f)
				)
			)

			// draw all the views
			for (view in slide.views) {
				when (view) {
					is SpaceFilling -> sphereRenderer.render(this, view)
				}
			}

			endRenderPass()
			end()
		}

		// render the frame
		queue.submit(
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
