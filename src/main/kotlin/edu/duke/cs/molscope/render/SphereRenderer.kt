package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.SIZE_BYTES
import cuchaz.kludge.tools.diff
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.view.SpaceFilling
import java.nio.file.Paths
import java.util.*


internal class SphereRenderer(
	val slideRenderer: SlideRenderer
): AutoCloseable {

	companion object {

		val vertexInput = VertexInput {
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
		}
	}

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	private fun <R> R.autoClose(block: R.() -> Unit): R = apply { closer.add { block() } }
	override fun close() = closer.close()

	// make the graphics pipeline
	val graphicsPipeline = slideRenderer
		.graphicsPipeline(
			listOf(
				slideRenderer.device.shaderModule(Paths.get("build/shaders/shader.vert.spv"))
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				slideRenderer.device.shaderModule(Paths.get("build/shaders/shader.geom.spv"))
					.autoClose()
					.stage("main", ShaderStage.Geometry),
				slideRenderer.device.shaderModule(Paths.get("build/shaders/shader.frag.spv"))
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			vertexInput,
			inputAssembly = InputAssembly(InputAssembly.Topology.PointList)
		)
		.autoClose()

	inner class Entry(val view: SpaceFilling): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
		override fun close() = closer.close()

		// allocate the vertex buffer on the GPU
		val vertexBuf = slideRenderer.device.
			buffer(
				size = view.buf.capacity().toLong(),
				usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
			.apply {

				// upload the vertex buffer
				transferHtoD { buf ->
					view.buf.rewind()
					buf.put(view.buf)
					buf.flip()
				}
			}
	}

	private val entries = IdentityHashMap<SpaceFilling,Entry>()
		.autoClose {
			for (entry in values) {
				entry.close()
			}
			clear()
		}

	fun update(views: List<SpaceFilling>) {
		views.diff(
			entries,
			added = { view ->
				entries[view] = Entry(view)
			},
			removed = { view, entry ->
				entry.close()
				entries.remove(view)
			}
		)
	}

	fun render(cmdbuf: CommandBuffer, view: SpaceFilling) = cmdbuf.apply {

		val entry = entries[view] ?: throw NoSuchElementException("call update() with this view, before render()")

		// draw geometry
		bindPipeline(graphicsPipeline)
		bindDescriptorSet(slideRenderer.descriptorSet, graphicsPipeline)
		bindVertexBuffer(entry.vertexBuf.buffer)
		draw(vertices = entry.view.numAtoms)
	}
}
