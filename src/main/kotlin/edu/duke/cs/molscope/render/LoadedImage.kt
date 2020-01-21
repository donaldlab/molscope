package edu.duke.cs.molscope.render

import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.position
import cuchaz.kludge.vulkan.*
import org.lwjgl.vulkan.VK10
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO


class LoadedImage internal constructor(
	internal val queue: Queue,
	rgba: ByteArray,
	val width: Int,
	val height: Int
) : AutoCloseable {

	internal constructor(queue: Queue, image: BufferedImage) : this(
		queue,
		(image.raster.dataBuffer as DataBufferByte).data,
		image.width,
		image.height
	)

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = apply { closer.add(this, replace) }
	override fun close() = closer.close()

	private val commandPool = queue.device
		.commandPool(queue.family)
		.autoClose()

	// allocate the image on the GPU
	private val gpuImage = queue.device.
		image(
			type = Image.Type.TwoD,
			extent = Extent3D(width, height, 1),
			format = Image.Format.R8G8B8A8_UNORM,
			usage = IntFlags.of(Image.Usage.TransferDst, Image.Usage.Sampled),
			tiling = Image.Tiling.Linear
			// TODO: Optimal tiling is better for rendering,
			//  but the HtoD transfer doesn't re-tile from Linear to Optimal
			//  so would need to find a way to do that somehow to support Optimal tiling
		)
		.autoClose()
		.allocateDevice()
		.autoClose()
		.apply {

			// upload image to the GPU
			transitionImage(IntFlags.of(Access.HostWrite), Image.Layout.General)
			transferHtoD { buf ->

				// upload the image to the GPU,
				// but respect the row pitch the GPU wants

				val srcPitch = width*4
				val dstPitch = image.getSubresourceLayout().rowPitch.toInt()

				for (y in 0 until height) {

					// copy the row
					buf.put(
						rgba,
						y*srcPitch,
						srcPitch
					)

					// fill the rest of the row with 0s
					buf.position += dstPitch - srcPitch
				}
				buf.flip()
			}

			// prep the image for the fragment shader
			transitionImage(IntFlags.of(Access.ShaderRead), Image.Layout.ShaderReadOnlyOptimal)
		}

	private fun Image.Allocated.transitionImage(access: IntFlags<Access>, layout: Image.Layout) {
		queue.submit(commandPool.buffer().apply {
			begin(IntFlags.of(CommandBuffer.Usage.OneTimeSubmit))

			pipelineBarrier(
				srcStage = IntFlags.of(PipelineStage.AllCommands),
				dstStage = IntFlags.of(PipelineStage.AllCommands),
				images = listOf(
					image.barrier(
						dstAccess = access,
						newLayout = layout
					)
				)
			)

			end()
		})
	}

	val view = gpuImage.image
		.view(
			// ImageIO has the color channels ABGR order for some reason
			components = Image.Components(
				Image.Swizzle.A,
				Image.Swizzle.B,
				Image.Swizzle.G,
				Image.Swizzle.R
			)
		)
		.autoClose()

	// make a sampler
	val sampler = queue.device
		.sampler()
		.autoClose()

	val descriptor: Imgui.ImageDescriptor by lazy {
		Imgui.imageDescriptor(view, sampler)
			.autoClose()
	}
}

/**
 * Convert the image bytes into an rgba buffer
 */
fun ByteArray.toBuffer(): BufferedImage =
	inputStream().use { stream ->
		ImageIO.read(stream)
	}
