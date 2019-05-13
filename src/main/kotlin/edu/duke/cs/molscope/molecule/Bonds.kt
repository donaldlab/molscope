package edu.duke.cs.molscope.molecule


/**
 * an efficient class for storing bond information
 *
 * tries to keep bond information in contigiuous blocks of memory,
 * which optimizes layout for rendering
 *
 * tries not to have an opinion about bond modeling choices,
 * eg, whether a bond is single, double, aromatic, etc
 *
 * doesn't directly maintain data structures to support querying bond graphs efficiently
 */
class Bonds {

	var size: Int = 0
		private set

	private val atomIndices = ArrayList<Int>()

	fun add(i1: Int, i2: Int) {
		atomIndices.add(i1)
		atomIndices.add(i2)
		size += 1
	}
}

data class Bond(
	val i1: Int,
	val i2: Int
) {

	override fun toString() = "($i1,$i2)"
}
