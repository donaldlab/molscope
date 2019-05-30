package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.tools.toStringAngstroms
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
internal class AmbientOcclusion(
	val slideRenderer: SlideRenderer
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = apply { closer.add(this, replace) }
	override fun close() = closer.close()


	private val device get() = slideRenderer.device
	private val queue get() = slideRenderer.queue

	private val descriptorPool = device.descriptorPool(
		maxSets = 1,
		sizes = DescriptorType.Counts(
			DescriptorType.StorageBuffer to 2,
			DescriptorType.StorageImage to 2
		)
	).autoClose()

	// make the compute pipeline
	private val atomsBinding = DescriptorSetLayout.Binding(
		binding = 0,
		type = DescriptorType.StorageBuffer,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val samplesBinding = DescriptorSetLayout.Binding(
		binding = 1,
		type = DescriptorType.StorageBuffer,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val occlusionXYBinding = DescriptorSetLayout.Binding(
		binding = 2,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val occlusionZBinding = DescriptorSetLayout.Binding(
		binding = 3,
		type = DescriptorType.StorageImage,
		stages = IntFlags.of(ShaderStage.Compute)
	)
	private val descriptorSetLayout = device.descriptorSetLayout(listOf(
		atomsBinding, samplesBinding, occlusionXYBinding, occlusionZBinding
	)).autoClose()
	private val descriptorSet = descriptorPool.allocate(descriptorSetLayout)
	private val pipeline = device // TODO: this can sometimes take several seconds to load, any way to fix?
		.computePipeline(
			stage = device.shaderModule(Paths.get("build/shaders/ambientOcclusion.comp.spv"))
				.autoClose()
				.stage("main", ShaderStage.Compute),
			descriptorSetLayouts = listOf(descriptorSetLayout),
			pushConstantRanges = listOf(
				PushConstantRange(IntFlags.of(ShaderStage.Compute), Float.SIZE_BYTES*8)
			)
		).autoClose()

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

	// make a command buffer
	private val commandPool = device
		.commandPool(
			queue.family,
			flags = IntFlags.of(CommandPool.Create.ResetCommandBuffer)
		)
		.autoClose()
	private val commandBuffer = commandPool.buffer()

	// make an interpolating sampler
	private val sampler = device.sampler(
		minFilter = Sampler.Filter.Linear,
		magFilter = Sampler.Filter.Linear,
		addressU = Sampler.Address.ClampToEdge,
		addressV = Sampler.Address.ClampToEdge,
		addressW = Sampler.Address.ClampToEdge
	).autoClose()

	private inner class OcclusionField(val extent: Extent3D) : AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
		override fun close() = closer.close()

		// allocate the occlusion images
		val occlusionXY = device
			.image(
				type = Image.Type.ThreeD,
				extent = extent,
				format = Image.Format.R8G8B8A8_UNORM,
				usage = IntFlags.of(Image.Usage.Storage, Image.Usage.Sampled)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		val occlusionXYView = occlusionXY.image.view().autoClose()

		val occlusionZ = device
			.image(
				type = Image.Type.ThreeD,
				extent = extent,
				format = Image.Format.R8G8_UNORM,
				usage = IntFlags.of(Image.Usage.Storage, Image.Usage.Sampled)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		val occlusionZView = occlusionZ.image.view().autoClose()

		// allocate the bounds buffer
		val boundsBuf = device
			.buffer(
				size = Int.SIZE_BYTES*4L + Float.SIZE_BYTES*8L,
				usage = IntFlags.of(Buffer.Usage.UniformBuffer)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()

		init {

			// update the descriptor sets
			device.updateDescriptorSets(
				writes = listOf(

					descriptorSet.address(occlusionXYBinding).write(
						DescriptorSet.ImageInfo(null, occlusionXYView, Image.Layout.General)
					),
					descriptorSet.address(occlusionZBinding).write(
						DescriptorSet.ImageInfo(null, occlusionZView, Image.Layout.General)
					),

					slideRenderer.mainDescriptorSet.address(slideRenderer.renderOcclusionXYBinding).write(
						DescriptorSet.ImageInfo(sampler, occlusionXYView, Image.Layout.ShaderReadOnlyOptimal)
					),
					slideRenderer.mainDescriptorSet.address(slideRenderer.renderOcclusionZBinding).write(
						DescriptorSet.ImageInfo(sampler, occlusionZView, Image.Layout.ShaderReadOnlyOptimal)
					),
					slideRenderer.mainDescriptorSet.address(slideRenderer.boundsBinding).write(
						DescriptorSet.BufferInfo(boundsBuf.buffer)
					)
				)
			)
		}
	}
	private var field: OcclusionField? = null

	private val sphereRenderables = IdentityHashMap<SphereRenderable,Nothing>()

	fun update(sphereRenderables: List<SphereRenderable>) {

		// if no changes, no need to update
		if (!sphereRenderables.changed(this.sphereRenderables.keys)) {
			return
		}

		// save the new renderables
		this.sphereRenderables.clear()
		sphereRenderables.forEach { this.sphereRenderables[it] = null }

		// combine all the atoms into a single occlusion buffer
		val field =
			OcclusionField(
				// TODO: make configurable?
				//extent = Extent3D(11, 11, 11)
				extent = Extent3D(32, 32, 32)
			)
			.autoClose(replace = field)
		this.field = field

		autoCloser {

			val numAtoms = sphereRenderables.sumBy { it.numVertices }

			// allocate the atoms buffer
			val atomsBuf = device
				.buffer(
					size = 16L + numAtoms*16L, // sizeof(struct Atom)
					usage = IntFlags.of(Buffer.Usage.StorageBuffer, Buffer.Usage.TransferDst)
				)
				.autoClose()
				.allocateDevice()
				.autoClose()

			val box = AABBf()
			val samples = HashSet<Offset3D>()

			fun Offset3D.neighbors(d: Int = 1) = ArrayList<Offset3D>().apply {
				for (z in max(0, z - d) .. min(field.extent.depth - 1, z + d)) {
					for (y in max(0, y - d) .. min(field.extent.height - 1, y + d)) {
						for (x in max(0, x - d) .. min(field.extent.width - 1, x + d)) {

							val sample = Offset3D(x, y, z)

							// skip the (gooey) center
							if (sample == this@neighbors) {
								continue
							}

							add(sample)
						}
					}
				}
			}

			fun Offset3D.manhattanDistance(other: Offset3D) =
				abs(x - other.x) + abs(y - other.y) + abs(z - other.z)

			// upload the atoms
			atomsBuf.transferHtoD { buf ->

				buf.putInt(numAtoms)
				buf.putInts(0, 0, 0) // padding

				// fill the buffer
				sphereRenderables.forEach { it.fillOcclusionBuffer(buf) }
				buf.flip()

				// expand the AABB
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

				// pad the box a little
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
								samples.add(sample)
								sample.neighbors().forEach { samples.add(it) }
							}
						}
					}
				}

				buf.rewind()

				// TEMP
				println("sampled ${samples.size}/${field.extent.width*field.extent.height*field.extent.depth} field grid points")
			}

			// TEMP
			println("bounding box: ${box.toStringAngstroms()}")
			println("      center: ${Vector3f((box.maxX + box.minX)/2, (box.maxY + box.minY)/2, (box.maxZ + box.minZ)/2).toStringAngstroms()}")
			println("        size: ${Vector3f(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).toStringAngstroms()}")

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

			// upload the bounds
			field.boundsBuf.transferHtoD { buf ->
				buf.putInts(
					field.extent.width,
					field.extent.height,
					field.extent.depth,
					0
				)
				buf.putFloats(
					box.minX, box.minY, box.minZ, 0f,
					box.maxX, box.maxY, box.maxZ, 0f
				)
				buf.flip()
			}

			// update the descriptor set
			device.updateDescriptorSets(
				writes = listOf(
					descriptorSet.address(atomsBinding).write(
						DescriptorSet.BufferInfo(atomsBuf.buffer)
					),
					descriptorSet.address(samplesBinding).write(
						DescriptorSet.BufferInfo(samplesBuf.buffer)
					)
				)
			)

			// TEMP
			val startNs = System.nanoTime()

			// call the compute shader
			queue.submit(
				commandBuffer.apply {
					begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

					// prep the occlusion images
					pipelineBarrier(
						srcStage = IntFlags.of(PipelineStage.TopOfPipe),
						dstStage = IntFlags.of(PipelineStage.ComputeShader),
						images = listOf(
							field.occlusionXY.image.barrier(
								dstAccess = IntFlags.of(Access.ShaderWrite),
								newLayout = Image.Layout.General
							),
							field.occlusionZ.image.barrier(
								dstAccess = IntFlags.of(Access.ShaderWrite),
								newLayout = Image.Layout.General
							)
						)
					)

					// launch the shader
					bindPipeline(pipeline)
					bindDescriptorSet(descriptorSet, pipeline)
					pushConstants(pipeline, IntFlags.of(ShaderStage.Compute),
						box.minX, box.minY, box.minZ, 0f,
						box.maxX, box.maxY, box.maxZ, 0f
					)
					dispatch(samples.size)

					end()
				}
			)
			queue.waitForIdle()

			// TEMP
			val elapsedNs = System.nanoTime() - startNs
			println("ambient occlusion shader time: ${elapsedNs/1_000} us")

			// TEMP: normalize the occlusion data
			// TODO: do this on the GPU
			field.occlusionZ.memory.map { buf ->

				val (rowPitch, depthPitch) = field.occlusionZ.image.getSubresourceLayout()
					.let { it.rowPitch.toInt() to it.depthPitch.toInt() }

				fun get(sample: Offset3D): Float {
					buf.position = sample.z*depthPitch + sample.y*rowPitch + sample.x*2
					return (buf.get().toInt() and 0xff)/255f
				}

				fun put(sample: Offset3D, occlusion: Float) {
					buf.position = sample.z*depthPitch + sample.y*rowPitch + sample.x*2
					buf.put((occlusion*255f).toInt().toByte())
				}

				// bound the range
				val outsideOcclusions = samples
					.mapNotNull { sample ->
						val occlusion = get(sample)
						if (occlusion == 1f) {
							null
						} else {
							occlusion
						}
					}
				val min = outsideOcclusions.min() ?: 0f
				val max = outsideOcclusions.max() ?: 0f
				println("occlusion range: [$min,$max]")
				// TODO: probably just need min, right?

				val weights = listOf(16f, 9f, 4f, 1f)

				// apply a 3x3x3 blur kernel to the occlusion field
				val newOcclusions = samples.associateWith { sample ->
					val occlusion = get(sample)

					// get all the outside neighbors, and weight the occlusions
					val chosenOcclusions = ArrayList<Float>()
					val chosenWeights = ArrayList<Float>()
					sample.neighbors()
						.filter { it in samples }
						.forEach { neighbor ->
							val neighborOcclusion = get(neighbor)
							if (neighborOcclusion != 1f) {
								val weight = weights[neighbor.manhattanDistance(sample)]
								chosenOcclusions.add(neighborOcclusion*weight)
								chosenWeights.add(weight)
							}
						}

					if (occlusion != 1f) {

						// also add the occlusion for this sample
						val weight = weights[0]
						chosenOcclusions.add(occlusion*weight)
						chosenWeights.add(weight)
					} /* else {
						occlusion is exactly 1, meaning it's inside some solid
						don't average this value, instead, replace it with the neighbor average
					}*/

					// replace with the weighted average
					if (chosenOcclusions.isNotEmpty()) {
						chosenOcclusions.sum()/chosenWeights.sum()
					} else {

						// no outside neighbors, just keep the original value
						occlusion
					}
				}

				// write the occlusions back
				for ((sample, occlusion) in newOcclusions) {
					put(sample, occlusion)
				}
			}
		}
	}

	fun barriers(cmdbuf: CommandBuffer) = cmdbuf.run {

		val field = field ?: return

		// prep the occlusion images
		pipelineBarrier(
			srcStage = IntFlags.of(PipelineStage.TopOfPipe),
			dstStage = IntFlags.of(PipelineStage.FragmentShader),
			images = listOf(
				field.occlusionXY.image.barrier(
					dstAccess = IntFlags.of(Access.ShaderRead),
					newLayout = Image.Layout.ShaderReadOnlyOptimal
				),
				field.occlusionZ.image.barrier(
					dstAccess = IntFlags.of(Access.ShaderRead),
					newLayout = Image.Layout.ShaderReadOnlyOptimal
				)
			)
		)
	}

	fun render(cmdbuf: CommandBuffer) = cmdbuf.run {

		val field = field ?: return

		// render it!
		bindPipeline(renderPipeline)
		bindDescriptorSet(slideRenderer.mainDescriptorSet, renderPipeline)
		draw(field.extent.run { width*height*depth })
	}
}
