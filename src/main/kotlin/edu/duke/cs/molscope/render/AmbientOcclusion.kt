package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.vulkan.Queue
import edu.duke.cs.molscope.tools.SphereGrid
import edu.duke.cs.molscope.tools.time
import org.joml.AABBf
import org.joml.Vector3f
import java.nio.file.Paths
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * An implementation of world-space ambient occlusion for lighting
 *
 * This technique is based loosely on the "Voxel Field Ambient Occlusion" technique presented in:
 * https://lambdacube3d.wordpress.com/2016/05/15/ambient-occlusion-fields/
 * https://www.reddit.com/r/gamedev/comments/4jhqot/ambient_occlusion_fields/
 */
internal class OcclusionCalculator(
	val queue: Queue
) : AutoCloseable {

	val device: Device get() = queue.device

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	override fun close() = closer.close()

	private val descriptorPool = device.descriptorPool(
		maxSets = 2,
		sizes = DescriptorType.Counts(
			DescriptorType.StorageBuffer to 3,
			DescriptorType.StorageImage to 3
		)
	).autoClose()

	// make the compute pipeline
	private val spheresBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.StorageBuffer,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val linesBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.StorageBuffer,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val samplesBinding = DescriptorSetLayout.Binding(
		binding = 2,
		type = DescriptorType.StorageBuffer,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val occlusionBinding = DescriptorSetLayout.Binding(
		binding = 3,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val descriptorSetLayout = device.descriptorSetLayout(listOf(
		spheresBinding, linesBinding, samplesBinding, occlusionBinding
	)).autoClose()
	private val descriptorSet = descriptorPool.allocate(descriptorSetLayout)
	private val pipeline = device
		.computePipeline(
			stage = device.shaderModule(Paths.get("build/shaders/ambientOcclusion.comp.spv"))
				.autoClose()
				.stage("main", ShaderStage.Compute),
			descriptorSetLayouts = listOf(descriptorSetLayout),
			pushConstantRanges = listOf(
				PushConstantRange(IntFlags.of(ShaderStage.Compute), 16*2)
			)
		).autoClose()

	// make the blur pipeline
	private val occlusionInBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val occlusionOutBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val blurDescriptorSetLayout = device.descriptorSetLayout(listOf(
		occlusionInBinding, occlusionOutBinding
	)).autoClose()
	private val blurDescriptorSet = descriptorPool.allocate(blurDescriptorSetLayout)
	private val blurPipeline = device
		.computePipeline(
			stage = device.shaderModule(Paths.get("build/shaders/occlusionBlur.comp.spv"))
				.autoClose()
				.stage("main", ShaderStage.Compute),
			descriptorSetLayouts = listOf(blurDescriptorSetLayout),
			pushConstantRanges = listOf(
				PushConstantRange(IntFlags.of(ShaderStage.Compute), 16*3)
			)
		).autoClose()

	// make a command buffer
	private val commandPool = device
		.commandPool(
			queue.family,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	private val commandBuffer = commandPool.buffer()

	fun calc(extent: Extent3D, gridSubdivisions: Int, renderables: ViewRenderables): OcclusionField = autoCloser {

		val field = OcclusionField(device, extent)

		// use a sphere grid to define which rays to shoot
		val sphereGrid = SphereGrid(gridSubdivisions)
			.filter {
				// keep only the directions facing the "front"
				// so we ignore antipodal directions (the sphere grid has some symmetry)
				if (it.z == 0.0) {
					if (it.y == 0.0) {
						it.x >= 0.0
					} else {
						it.y >= 0.0
					}
				} else {
					it.z >= 0.0
				}
			}
			.map { it.toFloat() }

		// each line supports two rays
		val maxOcclusion = sphereGrid.size*2

		// allocate the lines buffer from the sphere grid
		val linesBuf = device
			.buffer(
				size = 16L + sphereGrid.size*16L, // sizeof(ivec4), sizeof(vec4)
				usage = IntFlags.of(Buffer.Usage.StorageBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		linesBuf.transferHtoD { buf ->

			// write the header
			buf.putInt(sphereGrid.size)
			buf.putInts(0, 0, 0) // padding

			// write the lines
			for (v in sphereGrid) {
				buf.putFloats(v.x, v.y, v.z)
				buf.putFloat(0f) // padding
			}
		}

		val box = AABBf()
		val samples = HashSet<Offset3D>()

		// upload the spheres
		val numSpheres = renderables.spheres.sumBy { it.numVertices }
		val spheresBuf = device
			.buffer(
				size = 16L + numSpheres*16L, // sizeof(struct Atom)
				usage = IntFlags.of(Buffer.Usage.StorageBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		spheresBuf.transferHtoD { buf ->

			buf.putInt(numSpheres)
			buf.putInts(0, 0, 0) // padding

			// fill the buffer
			renderables.spheres.forEach { it.fillOcclusionBuffer(buf) }
			buf.flip()

			// calculate the AABB for the occlusion field
			buf.position = 16
			if (buf.hasRemaining()) {
				val x = buf.float
				val y = buf.float
				val z = buf.float
				val r = buf.float
				box.setMin(x - r, y - r, z - r)
				box.setMax(x + r, y + r, z + r)
			}
			while (buf.hasRemaining()) {
				val x = buf.float
				val y = buf.float
				val z = buf.float
				val r = buf.float
				box.expandToInclude(x - r, y - r, z - r)
				box.expandToInclude(x + r, y + r, z + r)
			}
			buf.rewind()

			// pad the box a little to give us some breathing room
			box.expand(0.1f)

			val gridPad = listOf(
				(box.maxX - box.minX)/field.extent.width,
				(box.maxY - box.minY)/field.extent.height,
				(box.maxZ - box.minZ)/field.extent.depth
			).max()!!

			fun Offset3D.isNearSurface(): Boolean {

				val samplePos = Vector3f(
					x.toFloat()*(box.maxX - box.minX)/(field.extent.width - 1) + box.minX,
					y.toFloat()*(box.maxY - box.minY)/(field.extent.height - 1) + box.minY,
					z.toFloat()*(box.maxZ - box.minZ)/(field.extent.depth - 1) + box.minZ
				)

				buf.position = 16
				while (buf.hasRemaining()) {
					val pos = Vector3f(buf.float, buf.float, buf.float)
					val r = buf.float

					// pad the radius by one grid spacing
					val rlo = r - gridPad
					val rhi = r + gridPad

					if (samplePos.distanceSquared(pos) in rlo*rlo .. rhi*rhi) {

						// found one!
						return true
					}
				}

				return false
			}

			// grab all the field grid points near surfaces
			for (z in 0 until field.extent.depth) {
				for (y in 0 until field.extent.height) {
					for (x in 0 until field.extent.width) {

						val sample = Offset3D(x, y, z)

						// is this sample near a surface?
						if (sample.isNearSurface()) {

							// accept the sample and its neighbors
							sample.neighbors(field.extent, includeCenter = true).forEach { samples.add(it) }
						}
					}
				}
			}

			buf.rewind()
		}

		// upload the samples
		val samplesBuf = device
			.buffer(
				size = 16L + samples.size*16L, // sizeof(uvec4)
				usage = IntFlags.of(Buffer.Usage.StorageBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		samplesBuf.transferHtoD { buf ->
			buf.putInts(
				field.extent.width,
				field.extent.height,
				field.extent.depth,
				0 // padding
			)
			for (sample in samples) {
				buf.putInts(
					sample.x,
					sample.y,
					sample.z,
					0 // padding
				)
			}
			buf.flip()
		}

		// allocate the occlusion image
		val occlusionImage = device
			.image(
				type = field.blurredOcclusionImage.image.type,
				extent = extent,
				format = field.blurredOcclusionImage.image.format,
				usage = IntFlags.of(Image.Usage.Storage, Image.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		val occlusionView = occlusionImage.image.view().autoClose()

		// update the descriptor sets
		device.updateDescriptorSets(
			writes = listOf(
				descriptorSet.address(spheresBinding).write(
					DescriptorSet.BufferInfo(spheresBuf.buffer)
				),
				descriptorSet.address(linesBinding).write(
					DescriptorSet.BufferInfo(linesBuf.buffer)
				),
				descriptorSet.address(samplesBinding).write(
					DescriptorSet.BufferInfo(samplesBuf.buffer)
				),
				descriptorSet.address(occlusionBinding).write(
					DescriptorSet.ImageInfo(null, occlusionView, Image.Layout.General)
				),

				blurDescriptorSet.address(occlusionInBinding).write(
					DescriptorSet.ImageInfo(null, occlusionView, Image.Layout.General)
				),
				blurDescriptorSet.address(occlusionOutBinding).write(
					DescriptorSet.ImageInfo(null, field.blurredOcclusionView, Image.Layout.General)
				)
			)
		)

		// TEMP
		time("occlusion") {

			// call the compute shader
			queue.submit(
				commandBuffer.apply {
					begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

					// prep the occlusion image
					pipelineBarrier(
						srcStage = IntFlags.of(PipelineStage.TopOfPipe),
						dstStage = IntFlags.of(PipelineStage.ComputeShader),
						images = listOf(
							occlusionImage.image.barrier(
								dstAccess = IntFlags.of(Access.ShaderRead, Access.ShaderWrite),
								newLayout = Image.Layout.General
							),
							field.blurredOcclusionImage.image.barrier(
								dstAccess = IntFlags.of(Access.ShaderWrite),
								newLayout = Image.Layout.General
							)
						)
					)

					// clear the occlusion image
					clearImage(occlusionImage.image, Image.Layout.General, ClearValue.Color.Int(0, 0, 0, 0))

					memstack { mem ->

						// run the occlusion kernel
						bindPipeline(pipeline)
						bindDescriptorSet(descriptorSet, pipeline)
						pushConstants(pipeline, IntFlags.of(ShaderStage.Compute), mem.malloc(16*2).apply {
							putFloats(box.minX, box.minY, box.minZ)
							putFloat(0f) // padding
							putFloats(box.maxX, box.maxY, box.maxZ)
							putFloat(0f) // padding
							flip()
						})
						dispatch(samples.size, sphereGrid.size)

						// run the blur kernel
						bindPipeline(blurPipeline)
						bindDescriptorSet(blurDescriptorSet, blurPipeline)
						pushConstants(blurPipeline, IntFlags.of(ShaderStage.Compute), mem.malloc(16*3).apply {
							putFloats(box.minX, box.minY, box.minZ)
							putFloat(0f) // padding
							putFloats(box.maxX, box.maxY, box.maxZ)
							putFloat(0f) // padding
							putInts(
								field.extent.width,
								field.extent.height,
								field.extent.depth
							)
							putInt(maxOcclusion)
							flip()
						})
						dispatch(field.extent)
					}

					end()
				}
			)
			queue.waitForIdle()
		}

		// upload the occlusion field info for fragment shaders
		field.boundsBuf.transferHtoD { buf ->
			buf.putInts(
				field.extent.width,
				field.extent.height,
				field.extent.depth
			)
			buf.putInt(maxOcclusion)
			buf.putFloats(
				box.minX, box.minY, box.minZ, 0f,
				box.maxX, box.maxY, box.maxZ, 0f
			)
			buf.flip()
		}

		return field
	}
}

internal class OcclusionField(
	device: Device,
	val extent: Extent3D
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	override fun close() = closer.close()

	// allocate an image for the final blurred occlusion data
	val blurredOcclusionImage = device
		.image(
			type = Image.Type.ThreeD,
			extent = extent,
			format = Image.Format.R32_SINT,
			usage = IntFlags.of(Image.Usage.Storage, Image.Usage.Sampled)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
	val blurredOcclusionView = blurredOcclusionImage.image.view().autoClose()

	// allocate the bounds buffer
	val boundsBuf = device
		.buffer(
			size = Int.SIZE_BYTES*4L + Float.SIZE_BYTES*8L,
			usage = IntFlags.of(Buffer.Usage.UniformBuffer)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	// make an interpolating sampler for the occlusion image
	private val sampler = device.sampler(
		minFilter = Sampler.Filter.Linear,
		magFilter = Sampler.Filter.Linear,
		addressU = Sampler.Address.ClampToEdge,
		addressV = Sampler.Address.ClampToEdge,
		addressW = Sampler.Address.ClampToEdge
	).autoClose()

	fun updateDescriptorSet(slideRenderer: SlideRenderer) {

		slideRenderer.device.updateDescriptorSets(
			writes = listOf(
				slideRenderer.mainDescriptorSet.address(slideRenderer.occlusionImageBinding).write(
					DescriptorSet.ImageInfo(sampler, blurredOcclusionView, Image.Layout.ShaderReadOnlyOptimal)
				),
				slideRenderer.mainDescriptorSet.address(slideRenderer.boundsBinding).write(
					DescriptorSet.BufferInfo(boundsBuf.buffer)
				)
			)
		)
	}
}


/**
 * A renderer for the occlusion field
 */
internal class OcclusionRenderer(
	val slideRenderer: SlideRenderer
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = apply { closer.add(this, replace) }
	override fun close() = closer.close()

	private val device get() = slideRenderer.device

	// make the render pipeline
	private val renderPipeline = slideRenderer
		.graphicsPipeline(
			listOf(
				device.shaderModule(Paths.get("build/shaders/ambientOcclusion.vert.spv"))
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				device.shaderModule(Paths.get("build/shaders/ambientOcclusion.geom.spv"))
					.autoClose()
					.stage("main", ShaderStage.Geometry),
				device.shaderModule(Paths.get("build/shaders/ambientOcclusion.frag.spv"))
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			inputAssembly = InputAssembly(InputAssembly.Topology.PointList)
		)
		.autoClose()

	fun barriers(cmdbuf: CommandBuffer, field: OcclusionField) = cmdbuf.run {

		// prep the occlusion images
		pipelineBarrier(
			srcStage = IntFlags.of(PipelineStage.TopOfPipe),
			dstStage = IntFlags.of(PipelineStage.FragmentShader),
			images = listOf(
				field.blurredOcclusionImage.image.barrier(
					dstAccess = IntFlags.of(Access.ShaderRead),
					newLayout = Image.Layout.ShaderReadOnlyOptimal
				)
			)
		)
	}

	fun render(cmdbuf: CommandBuffer, field: OcclusionField) = cmdbuf.run {

		// render it!
		bindPipeline(renderPipeline)
		bindDescriptorSet(slideRenderer.mainDescriptorSet, renderPipeline)
		draw(field.extent.run { width*height*depth })
	}
}

private fun Offset3D.neighbors(extent: Extent3D, includeCenter: Boolean, d: Int = 1) = ArrayList<Offset3D>().apply {
	for (z in max(0, z - d) .. min(extent.depth - 1, z + d)) {
		for (y in max(0, y - d) .. min(extent.height - 1, y + d)) {
			for (x in max(0, x - d) .. min(extent.width - 1, x + d)) {

				val sample = Offset3D(x, y, z)

				// skip the (gooey) center if needed
				if (!includeCenter && sample == this@neighbors) {
					continue
				}

				add(sample)
			}
		}
	}
}

private fun Offset3D.manhattanDistance(other: Offset3D) =
	abs(x - other.x) + abs(y - other.y) + abs(z - other.z)
