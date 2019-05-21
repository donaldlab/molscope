package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.SIZE_BYTES
import cuchaz.kludge.tools.diff
import cuchaz.kludge.vulkan.*
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*


internal class CylinderRenderer(
	val slideRenderer: SlideRenderer
): AutoCloseable {

	companion object {

		val vertexInput = VertexInput {
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
				slideRenderer.device.shaderModule(Paths.get("build/shaders/cylinder.vert.spv"))
					.autoClose()
					.stage("main", ShaderStage.Vertex),
				slideRenderer.device.shaderModule(Paths.get("build/shaders/cylinder.geom.spv"))
					.autoClose()
					.stage("main", ShaderStage.Geometry),
				slideRenderer.device.shaderModule(Paths.get("build/shaders/cylinder.frag.spv"))
					.autoClose()
					.stage("main", ShaderStage.Fragment)
			),
			vertexInput,
			inputAssembly = InputAssembly(InputAssembly.Topology.LineList)
		)
		.autoClose()

	inner class Entry(src: CylinderRenderable): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = apply { closer.add(this) }
		override fun close() = closer.close()

		// allocate the vertex buffer on the GPU
		val vertexBuf = slideRenderer.device
			.buffer(
				size = src.vertexBuf.capacity().toLong(),
				usage = IntFlags.of(Buffer.Usage.VertexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
			.apply {

				// upload the vertex buffer
				transferHtoD { buf ->
					src.vertexBuf.rewind()
					buf.put(src.vertexBuf)
					buf.flip()
				}
			}

		// allocate the index buffer on the GPU
		val indexBuf = slideRenderer.device
			.buffer(
				size = src.indexBuf.capacity().toLong(),
				usage = IntFlags.of(Buffer.Usage.IndexBuffer, Buffer.Usage.TransferDst)
			)
			.autoClose()
			.allocateDevice()
			.autoClose()
			.apply {

				// upload the index buffer
				transferHtoD { buf ->
					src.indexBuf.rewind()
					buf.put(src.indexBuf)
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
	}

	fun render(cmdbuf: CommandBuffer, src: CylinderRenderable) = cmdbuf.apply {

		val entry = entries[src] ?: throw NoSuchElementException("call update() with this source, before render()")

		// draw geometry
		bindPipeline(graphicsPipeline)
		bindDescriptorSet(slideRenderer.descriptorSet, graphicsPipeline)
		bindVertexBuffer(entry.vertexBuf.buffer)
		bindIndexBuffer(entry.indexBuf.buffer, CommandBuffer.IndexType.UInt32)
		drawIndexed(indices = src.numIndices)
	}
}


internal interface CylinderRenderable {
	val numIndices: Int
	val vertexBuf: ByteBuffer
	val indexBuf: ByteBuffer
}