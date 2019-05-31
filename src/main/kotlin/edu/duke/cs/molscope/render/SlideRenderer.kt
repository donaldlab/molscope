package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.vulkan.Queue
import edu.duke.cs.molscope.CameraCommand
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.view.BallAndStick
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

	var cursorPos: Offset2D? = null
	var cursorIndex: CursorIndex? = null
		private set

	// get the old settings (and mark them dirty) or make new settings
	val settings: RenderSettings = oldRenderer
		?.settings
		?.apply { dirty = true }
		?: RenderSettings()

	// make the main render pass
	val colorAttachment =
		Attachment(
			format = Image.Format.R8G8B8A8_UNORM,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			// finish this on ShaderReadOnlyOptimal, so WindowRenderer can sample it
			finalLayout = Image.Layout.ShaderReadOnlyOptimal
		)
	val indexAttachment =
		Attachment(
			format = Image.Format.R32G32_SINT,
			loadOp = LoadOp.Clear,
			storeOp =  StoreOp.Store,
			finalLayout = Image.Layout.ShaderReadOnlyOptimal
		)
	val depthAttachment =
		Attachment(
			format = Image.Format.D32_SFLOAT,
			loadOp = LoadOp.Clear,
			storeOp = StoreOp.Store,
			finalLayout = Image.Layout.DepthStencilAttachmentOptimal
		)
	val subpass =
		Subpass(
			pipelineBindPoint = PipelineBindPoint.Graphics,
			colorAttachments = listOf(
				colorAttachment to Image.Layout.ColorAttachmentOptimal,
				indexAttachment to Image.Layout.ColorAttachmentOptimal
			),
			depthStencilAttachment = depthAttachment to Image.Layout.DepthStencilAttachmentOptimal
		)
	val renderPass = device
		.renderPass(
			attachments = listOf(colorAttachment, indexAttachment, depthAttachment),
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
		)
		.autoClose()

	// make the post processing render pass
	val postSubpass =
		Subpass(
			pipelineBindPoint = PipelineBindPoint.Graphics,
			colorAttachments = listOf(colorAttachment to Image.Layout.ColorAttachmentOptimal)
		)
	val postRenderPass = device
		.renderPass(
			attachments = listOf(colorAttachment),
			subpasses = listOf(postSubpass),
			subpassDependencies = listOf(
				SubpassDependency(
					src = Subpass.External.dependency(
						stage = IntFlags.of(PipelineStage.ColorAttachmentOutput)
					),
					dst = postSubpass.dependency(
						stage = IntFlags.of(PipelineStage.ColorAttachmentOutput),
						access = IntFlags.of(Access.ColorAttachmentRead, Access.ColorAttachmentWrite)
					)
				)
			)
		)
		.autoClose()

	// make the render images
	val colorImage = device
		.image(
			Image.Type.TwoD,
			extent.to3D(1),
			colorAttachment.format,
			IntFlags.of(Image.Usage.ColorAttachment, Image.Usage.Storage, Image.Usage.InputAttachment)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val colorView = colorImage.image.view().autoClose()

	val indexImage = device
		.image(
			Image.Type.TwoD,
			extent.to3D(1),
			indexAttachment.format,
			IntFlags.of(Image.Usage.ColorAttachment, Image.Usage.Storage, Image.Usage.InputAttachment)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val indexView = indexImage.image.view().autoClose()

	val postImage = device
		.image(
			Image.Type.TwoD,
			extent.to3D(1),
			colorAttachment.format,
			IntFlags.of(Image.Usage.ColorAttachment, Image.Usage.Sampled)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val postView = postImage.image.view().autoClose()

	val sampler = device.sampler().autoClose()

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
			imageViews = listOf(colorView, indexView, depthView),
			extent = extent
		)
		.autoClose()

	// make a framebuffer for the post pass too
	val postFramebuffer = device
		.framebuffer(
			postRenderPass,
			imageViews = listOf(postView),
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
		maxSets = 3,
		sizes = DescriptorType.Counts(
			DescriptorType.UniformBuffer to 3,
			DescriptorType.StorageBuffer to 2,
			DescriptorType.StorageImage to 3,
			DescriptorType.CombinedImageSampler to 1
		)
	).autoClose()

	// make the main descriptor set
	val viewBufBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.UniformBuffer,
		stages = IntFlags.of(ShaderStage.Vertex, ShaderStage.Geometry, ShaderStage.Fragment)
	)
	val occlusionImageBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.CombinedImageSampler,
		stages = IntFlags.of(ShaderStage.Fragment)
	)
	val boundsBinding = DescriptorSetLayout.Binding(
		binding = 2,
		type = DescriptorType.UniformBuffer,
		stages = IntFlags.of(ShaderStage.Vertex, ShaderStage.Fragment)
	)
	val settingsBinding = DescriptorSetLayout.Binding(
		binding = 3,
		type = DescriptorType.UniformBuffer,
		stages = IntFlags.of(ShaderStage.Fragment)
	)
	val mainDescriptorSetLayout = device.descriptorSetLayout(listOf(
		viewBufBinding, occlusionImageBinding, boundsBinding, settingsBinding
	)).autoClose()
	val mainDescriptorSet = descriptorPool.allocate(mainDescriptorSetLayout)

	// make the cursor descriptor set
	val cursorBufBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.StorageBuffer,
		// TODO: effects in geometry shader too? (eg, expand billboards for fades/blurs?)
		stages = IntFlags.of(ShaderStage.Compute, ShaderStage.Fragment)
	)
	val indexImageBinding = DescriptorSetLayout.Binding(
		binding = 2,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute, ShaderStage.Fragment)
	)
	val cursorDescriptorSetLayout = device.descriptorSetLayout(listOf(
		cursorBufBinding, indexImageBinding
	)).autoClose()
	val cursorDescriptorSet = descriptorPool.allocate(cursorDescriptorSetLayout)

	// make the post descriptor set
	val colorImageBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Fragment)
	)
	val postDescriptorSetLayout = device.descriptorSetLayout(listOf(
		cursorBufBinding, colorImageBinding, indexImageBinding
	)).autoClose()
	val postDescriptorSet = descriptorPool.allocate(postDescriptorSetLayout)

	// make a graphics command buffer
	val commandPool = device
		.commandPool(
			queue.family,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	val commandBuffer = commandPool.buffer()

	// allocate the settings buffer
	val settingsBuf = device
		.buffer(
			size = Float.SIZE_BYTES*3L,
			usage = IntFlags.of(Buffer.Usage.UniformBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	// allocate the cursor buffer on the device
	val cursorBufDevice = device
		.buffer(
			size = Int.SIZE_BYTES*6L,
			usage = IntFlags.of(Buffer.Usage.StorageBuffer, Buffer.Usage.TransferDst, Buffer.Usage.TransferSrc)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	// allocate another cursor buffer on the host
	val cursorBufHost = device
		.buffer(
			size = cursorBufDevice.buffer.size,
			usage = IntFlags.of(Buffer.Usage.TransferDst, Buffer.Usage.TransferSrc)
		)
		.autoClose()
		.allocateHost()
		.autoClose()

	init {
		// update the descriptor sets
		device.updateDescriptorSets(
			writes = listOf(
				mainDescriptorSet.address(viewBufBinding).write(
					DescriptorSet.BufferInfo(camera.buf.buffer)
				),
				// AmbientOcclusion updates some of the main bindings
				mainDescriptorSet.address(settingsBinding).write(
					DescriptorSet.BufferInfo(settingsBuf.buffer)
				),
				cursorDescriptorSet.address(cursorBufBinding).write(
					DescriptorSet.BufferInfo(cursorBufDevice.buffer)
				),
				cursorDescriptorSet.address(indexImageBinding).write(
					DescriptorSet.ImageInfo(null, indexView, Image.Layout.General)
				),
				postDescriptorSet.address(cursorBufBinding).write(
					DescriptorSet.BufferInfo(cursorBufDevice.buffer)
				),
				postDescriptorSet.address(colorImageBinding).write(
					DescriptorSet.ImageInfo(null, colorView, Image.Layout.General)
				),
				postDescriptorSet.address(indexImageBinding).write(
					DescriptorSet.ImageInfo(null, indexView, Image.Layout.General)
				)
			)
		)
	}

	/**
	 * next descriptor set layout binding = 3
	 * next push constant range offset = 16
	 */
	fun graphicsPipeline(
		stages: List<ShaderModule.Stage>,
		vertexInput: VertexInput = VertexInput(),
		inputAssembly: InputAssembly,
		descriptorSetLayouts: List<DescriptorSetLayout> = emptyList(),
		pushConstantRanges: List<PushConstantRange> = emptyList()
	) = device.graphicsPipeline(
		renderPass,
		stages,
		descriptorSetLayouts = listOf(mainDescriptorSetLayout) + descriptorSetLayouts,
		pushConstantRanges = listOf(
				PushConstantRange(IntFlags.of(ShaderStage.Fragment), 16)
			) + pushConstantRanges,
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

			// use typical alpha blending
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
			),

			// always overwrite the dest (framebuf) values
			indexAttachment to ColorBlendState.Attachment(
				color = ColorBlendState.Attachment.Part(
					src = BlendFactor.One,
					dst = BlendFactor.Zero,
					op = BlendOp.Add
				),
				alpha = ColorBlendState.Attachment.Part(
					src = BlendFactor.One,
					dst = BlendFactor.Zero,
					op = BlendOp.Add
				)
			)
		),
		depthStencilState = DepthStencilState()
	)

	// make a compute shader to download the index under the cursor
	private val cursorPipeline = device
		.computePipeline(
			stage = device.shaderModule(Shaders["cursorIndex.comp"])
				.autoClose()
				.stage("main", ShaderStage.Compute),
			descriptorSetLayouts = listOf(cursorDescriptorSetLayout)
		).autoClose()

	private val postPipeline = device
		.graphicsPipeline(
			postRenderPass,
			stages = listOf(
				device.shaderModule(Shaders["post.vert"])
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				device.shaderModule(Shaders["post.frag"])
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			descriptorSetLayouts = listOf(postDescriptorSetLayout),
			inputAssembly = InputAssembly(InputAssembly.Topology.TriangleStrip),
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
			)
		)
		.autoClose()

	private val sphereRenderer = SphereRenderer(this).autoClose()
	private val cylinderRenderer = CylinderRenderer(this).autoClose()

	private val occlusionRenderer = OcclusionRenderer(this).autoClose()

	fun render(slide: Slide.Locked, renderables: ViewRenderables, occlusionField: OcclusionField, renderFinished: Semaphore? = null) {

		sphereRenderer.update(renderables.spheres)
		cylinderRenderer.update(renderables.cylinders)

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

		// update the hover buffer
		cursorBufHost.memory.map { buf ->
			val cursorPos = cursorPos
			if (cursorPos != null) {
				buf.putInt(1)
				buf.skip(4)
				buf.putInt(cursorPos.x)
				buf.putInt(cursorPos.y)
				buf.putInt(-1)
				buf.putInt(-1)
			} else {
				buf.putInt(0)
			}
			buf.flip()
		}

		// update the settings buffer if needed
		if (settings.dirty) {
			settingsBuf.transferHtoD { buf ->
				buf.putFloat(settings.lightingWeight)
				buf.putFloat(settings.depthWeight)
				buf.putFloat(settings.ambientOcclusionWeight)
				buf.flip()
			}
			settings.dirty = false
		}

		// record the command buffer
		commandBuffer.apply {
			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			// upload the cursor buffer
			copyBuffer(cursorBufHost.buffer, cursorBufDevice.buffer)

			// get the framebuffer attachments ready for rendering
			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.TopOfPipe),
				dstStage = IntFlags.of(PipelineStage.ColorAttachmentOutput),
				images = listOf(
					colorImage.image.barrier(
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

			occlusionRenderer.barriers(this, occlusionField)

			// draw all the views
			beginRenderPass(
				renderPass,
				framebuffer,
				rect,
				clearValues = mapOf(
					colorAttachment to backgroundColor.toClearColor(),
					indexAttachment to ClearValue.Color.Int(-1, -1, -1, -1), // -1 as int
					depthAttachment to ClearValue.DepthStencil(depth = 1f)
				)
			)
			slide.views.forEachIndexed { i, view ->
				when (view) {
					is SpaceFilling -> {
						sphereRenderer.render(this, view.sphereRenderable, i)
					}
					is BallAndStick -> {
						sphereRenderer.render(this, view.sphereRenderable, i)
						cylinderRenderer.render(this, view.cylinderRenderable, i)
					}
				}
			}
			if (settings.showOcclusionField) {
				occlusionRenderer.render(this, occlusionField)
			}
			endRenderPass()

			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.TopOfPipe),
				dstStage = IntFlags.of(PipelineStage.FragmentShader),
				images = listOf(
					colorImage.image.barrier(
						dstAccess = IntFlags.of(Access.ShaderRead),
						newLayout = Image.Layout.General
					),
					indexImage.image.barrier(
						dstAccess = IntFlags.of(Access.ShaderRead),
						newLayout = Image.Layout.General
					)
				)
			)

			cursorPos?.let { cursorPos ->

				// figure out what was under the cursor
				pipelineBarrier(
					srcStage = IntFlags.of(PipelineStage.Transfer),
					dstStage = IntFlags.of(PipelineStage.ComputeShader),
					buffers = listOf(
						cursorBufDevice.buffer.barrier(
							dstAccess = IntFlags.of(Access.ShaderRead, Access.ShaderWrite)
						)
					)
				)

				bindPipeline(cursorPipeline)
				bindDescriptorSet(cursorDescriptorSet, cursorPipeline)
				dispatch(1)

				// download the cursor buffer so we can read the index on the host side
				copyBuffer(cursorBufDevice.buffer, cursorBufHost.buffer)
			}

			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.ComputeShader),
				dstStage = IntFlags.of(PipelineStage.FragmentShader),
				buffers = listOf(
					cursorBufDevice.buffer.barrier(
						dstAccess = IntFlags.of(Access.ShaderRead)
					)
				)
			)

			// do the post-processing pass
			beginRenderPass(
				postRenderPass,
				postFramebuffer,
				rect,
				clearValues = mapOf(colorAttachment to ClearValue.Color.Int(0, 0, 0, 0))
			)
			bindPipeline(postPipeline)
			bindDescriptorSet(postDescriptorSet, postPipeline)
			draw(4)
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

		// read the cursor index, if any
		if (cursorPos != null) {

			// TODO: is there a more efficient wait mechanism here?
			// we only need to wait for the buffer download, not the whole post-processing step
			queue.waitForIdle()

			cursorBufHost.memory.map { buf ->

				buf.position(Int.SIZE_BYTES*4)
				val index = buf.int
				val viewIndex = buf.int

				cursorIndex = CursorIndex(viewIndex, index)
			}

		} else {
			cursorIndex = null
		}
	}
}

data class CursorIndex(val viewIndex: Int, val index: Int)


internal data class ViewRenderables(
	val spheres: List<SphereRenderable>,
	val cylinders: List<CylinderRenderable>
)

class RenderSettings {

	var lightingWeight: Float = 1f
		set(value) {
			dirty = dirty || value != field
			field = value
		}

	var depthWeight: Float = 0.2f
		set(value) {
			dirty = dirty || value != field
			field = value
		}

	var ambientOcclusionWeight: Float = 1f
		set(value) {
			dirty = dirty || value != field
			field = value
		}

	var showOcclusionField: Boolean = false

	/** set true if we need to upload the render settings buffer */
	internal var dirty = true
}
