package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.toByteBuffer
import java.nio.ByteBuffer


object Shaders {

	operator fun get(name: String): ByteBuffer {
		javaClass.getResourceAsStream("../shaders/$name.spv").use {
			return it.readBytes().toByteBuffer()
		}
	}
}
