package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.Element
import org.joml.AABBf
import org.joml.Vector3f
import java.nio.file.Paths


class SlideRenderer(
	val device: Device,
	val queue: Queue,
	val width: Int,
	val height: Int,
	var backgroundColor: ColorRGBA = ColorRGBA.Float(0f, 0f, 0f)
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

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

	// TEMP: an N-terminal alanine
	data class Atom(val x: Float, val y: Float, val z: Float, val element: Element)
	val atoms = listOf(
		Atom(14.699f, 27.060f, 24.044f, Element.Nitrogen),
		Atom(15.468f, 27.028f, 24.699f, Element.Hydrogen),
		Atom(15.072f, 27.114f, 23.102f, Element.Hydrogen),
		Atom(14.136f, 27.880f, 24.237f, Element.Hydrogen),
		Atom(13.870f, 25.845f, 24.199f, Element.Carbon),
		Atom(14.468f, 24.972f, 23.937f, Element.Hydrogen),
		Atom(13.449f, 25.694f, 25.672f, Element.Carbon),
		Atom(12.892f, 24.768f, 25.807f, Element.Hydrogen),
		Atom(14.334f, 25.662f, 26.307f, Element.Hydrogen),
		Atom(12.825f, 26.532f, 25.978f, Element.Hydrogen),
		Atom(12.685f, 25.887f, 23.222f, Element.Carbon),
		Atom(11.551f, 25.649f, 23.607f, Element.Oxygen)
	)

	// make a camera
	val camera = Camera(device)
		.autoClose()
		.apply {

			// compute the atom bounding box
			val box = atoms[0].run {
				AABBf(
					x, y, z,
					x, y, z
				)
			}
			for (atom in atoms) {
				box.expandToInclude(
					atom.x - atom.element.radius,
					atom.y - atom.element.radius,
					atom.z - atom.element.radius
				)
				box.expandToInclude(
					atom.x + atom.element.radius,
					atom.y + atom.element.radius,
					atom.z + atom.element.radius
				)
			}

			// add a little padding to give the box some breathing room
			box.expand(1f)

			lookAtBox(
				width, height,
				focalLength = 200f,
				look = Vector3f(0f, 0f, 1f),
				up = Vector3f(0f, 1f, 0f),
				box = box
			)
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
	).autoClose()

	// allocate the vertex buffer on the GPU
	val vertexBuf = device.
		buffer(
			size = graphicsPipeline.vertexInput.size*atoms.size,
			usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
		.apply {

			// upload geometry to the vertex buffer
			transferHtoD { buf ->

				for (atom in atoms) {
					buf.putFloat(atom.x)
					buf.putFloat(atom.y)
					buf.putFloat(atom.z)
					buf.putFloat(atom.element.radius)
					buf.putColor4Bytes(atom.element.color)
				}
				buf.flip()
			}
		}

	fun render(renderFinished: Semaphore? = null) {

		// update the view buffer
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

			// draw geometry
			bindPipeline(graphicsPipeline)
			bindDescriptorSet(descriptorSet, graphicsPipeline)
			bindVertexBuffer(vertexBuf.buffer)
			draw(vertices = atoms.size)

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
