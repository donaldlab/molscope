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
class Bonds(): Iterable<Bond> {

	var size: Int = 0
		private set

	private val atomIndices = ArrayList<Int>()

	constructor(other: Bonds) : this() {
		this.size = other.size
		this.atomIndices.addAll(other.atomIndices)
	}
	fun copy() = Bonds(this)

	fun add(i1: Int, i2: Int) {
		atomIndices.add(i1)
		atomIndices.add(i2)
		size += 1
	}

	override fun iterator() =
		object : Iterator<Bond> {

			var i = 0

			override fun hasNext() = i < size

			override fun next() =
				get(i)
				.also { i += 1 }
		}

	private fun checkIndex(i: Int) {
		if (i < 0 || i >= size) {
			throw IllegalArgumentException("index $i is out of range [0,$size)")
		}
	}

	operator fun get(i: Int): Bond {
		checkIndex(i)
		return Bond(atomIndices[i*2], atomIndices[i*2 + 1])
	}
}

data class Bond(
	val i1: Int,
	val i2: Int
) {

	override fun toString() = "($i1,$i2)"
}
