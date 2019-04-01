package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser


class HeadlessRenderer(
	val renderer: Renderer
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

	// TODO
}