package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.SIZE_BYTES
import cuchaz.kludge.tools.diff
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.view.ColorsMode
import org.joml.AABBf
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max


internal class CylinderRenderer(
	val slideRenderer: SlideRenderer
): AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
	private fun <R> R.autoClose(block: R.() -> Unit): R = apply { closer.add { block() } }
	override fun close() = closer.close()

	// make the graphics pipeline
	val graphicsPipeline = slideRenderer
		.graphicsPipeline(
			listOf(
				slideRenderer.device.shaderModule(Shaders["cylinder.vert"])
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				slideRenderer.device.shaderModule(Shaders["cylinder.geom"])
					.autoClose()
					.stage("main", ShaderStage.Geometry),
				slideRenderer.device.shaderModule(Shaders["cylinder.frag"])
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			vertexInput = VertexInput {
				binding(stride = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4 + Int.SIZE_BYTES*2) {
					// pos
					attribute(
						location = 0,
						format = Image.Format.R32G32B32_SFLOAT,
						offset = 0
					)
					// radius
					attribute(
						location = 1,
						format = Image.Format.R32_SFLOAT,
						offset = Float.SIZE_BYTES*3
					)
					// color
					attribute(
						location = 2,
						format = Image.Format.R8G8B8A8_UNORM,
						offset = Float.SIZE_BYTES*4
					)
					// target index
					attribute(
						location = 3,
						format = Image.Format.R32_SINT,
						offset = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4
					)
					// effects
					attribute(
						location = 4,
						format = Image.Format.R8G8B8A8_UINT,
						offset = Float.SIZE_BYTES*4 + Byte.SIZE_BYTES*4 + Int.SIZE_BYTES
					)
				}
			},
			inputAssembly = InputAssembly(InputAssembly.Topology.LineList)
		)
		.autoClose()

	inner class Entry(val src: CylinderRenderable): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
		override fun close() = closer.close()

		// Vulkan won't allow 0-sized buffers, so use at least one byte
		private fun bufSize(size: Long) = max(1L, size)

		// allocate the vertex buffer on the GPU
		val vertexBuf = slideRenderer.device
			.buffer(
				size = bufSize(src.numVertices*graphicsPipeline.vertexInput.size),
				usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()

		// allocate the index buffer on the GPU
		val indexBuf = slideRenderer.device
			.buffer(
				size = bufSize(src.numIndices*Int.SIZE_BYTES.toLong()),
				usage = IntFlags.of(Buffer.Usage.IndexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()

		private val dirtyness = Dirtyness()

		fun update(colorsMode: ColorsMode) {

			// track state changes
			dirtyness.update(colorsMode, src.verticesSequence, src.indicesSequence)
			if (!dirtyness.isDirty) {
				return
			}

			// update buffers
			vertexBuf.transferHtoD { buf ->
				src.fillVertexBuffer(buf, colorsMode)
				buf.flip()
			}
			indexBuf.transferHtoD { buf ->
				src.fillIndexBuffer(buf)
				buf.flip()
			}
		}
	}

	private val entries = IdentityHashMap<CylinderRenderable,Entry>()
		.autoClose {
			for (entry in values) {
				entry.close()
			}
			clear()
		}

	fun update(sources: List<CylinderRenderable>) {

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

	fun render(cmdbuf: CommandBuffer, src: CylinderRenderable, viewIndex: Int) = cmdbuf.apply {

		if (src.numIndices > 0) {

			val entry = entries[src] ?: throw NoSuchElementException("call update() with this source, before render()")

			// draw geometry
			bindPipeline(graphicsPipeline)
			bindDescriptorSet(slideRenderer.mainDescriptorSet, graphicsPipeline)
			bindVertexBuffer(entry.vertexBuf.buffer)
			bindIndexBuffer(entry.indexBuf.buffer, CommandBuffer.IndexType.UInt32)
			pushConstants(graphicsPipeline, IntFlags.of(ShaderStage.Fragment),
				viewIndex, 0, 0, 0
			)
			drawIndexed(indices = src.numIndices)
		}
	}
}


internal interface CylinderRenderable {
	val numVertices: Int
	val verticesSequence: Int
	fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode)
	val numIndices: Int
	val indicesSequence: Int
	fun fillIndexBuffer(buf: ByteBuffer)
	val boundingBox: AABBf
	fun fillOcclusionBuffer(buf: ByteBuffer)
}
