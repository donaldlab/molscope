package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.SIZE_BYTES
import cuchaz.kludge.tools.diff
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.view.ColorsMode
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*


internal class SphereRenderer(
	val slideRenderer: SlideRenderer
): AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	private fun <R> R.autoClose(block: R.() -> Unit): R = apply { closer.add { block() } }
	override fun close() = closer.close()
	
	private val device get() = slideRenderer.device

	// make the graphics pipeline
	val graphicsPipeline = slideRenderer
		.graphicsPipeline(
			listOf(
				device.shaderModule(Paths.get("build/shaders/sphere.vert.spv"))
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				device.shaderModule(Paths.get("build/shaders/sphere.geom.spv"))
					.autoClose()
					.stage("main", ShaderStage.Geometry),
				device.shaderModule(Paths.get("build/shaders/sphere.frag.spv"))
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			vertexInput = VertexInput {
				binding(stride = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4 + Int.SIZE_BYTES) {
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
					attribute(
						location = 3,
						format = Image.Format.R32_SINT,
						offset = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4
					)
				}
			},
			inputAssembly = InputAssembly(InputAssembly.Topology.PointList)
		)
		.autoClose()

	inner class Entry(val src: SphereRenderable): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
		override fun close() = closer.close()

		// allocate the vertex buffer on the GPU
		val vertexBuf = device
			.buffer(
				size = src.numVertices*graphicsPipeline.vertexInput.size,
				usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
		
		private val dirtyness = Dirtyness()

		fun update(colorsMode: ColorsMode) {

			// track state changes
			dirtyness.update(colorsMode)
			if (!dirtyness.isDirty) {
				return
			}

			// update buffers
			vertexBuf.transferHtoD { buf ->
				src.fillVertexBuffer(buf, colorsMode)
				buf.flip()
			}
		}
	}

	private val entries = IdentityHashMap<SphereRenderable,Entry>()
		.autoClose {
			for (entry in values) {
				entry.close()
			}
			clear()
		}

	fun update(sources: List<SphereRenderable>) {

		// add/remove entries
		sources.diff(
			entries,
			added = { src ->
				entries[src] = Entry(src)
			},
			removed = { src, entry ->
				entry.close()
				entries.remove(src)
			}
		)

		// update entries
		for (entry in entries.values) {
			entry.update(ColorsMode.current)
		}
	}

	fun render(cmdbuf: CommandBuffer, src: SphereRenderable, viewIndex: Int) = cmdbuf.apply {

		val entry = entries[src] ?: throw NoSuchElementException("call update() with this source, before render()")

		// draw geometry
		bindPipeline(graphicsPipeline)
		bindDescriptorSet(slideRenderer.mainDescriptorSet, graphicsPipeline)
		bindVertexBuffer(entry.vertexBuf.buffer)
		pushConstants(graphicsPipeline, IntFlags.of(ShaderStage.Fragment),
			viewIndex, 0, 0, 0
		)
		draw(vertices = src.numVertices)
	}
}


internal interface SphereRenderable {
	val numVertices: Int
	fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode)
	fun fillOcclusionBuffer(buf: ByteBuffer)
}
