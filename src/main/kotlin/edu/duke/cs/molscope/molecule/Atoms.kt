package edu.duke.cs.molscope.molecule

import org.joml.Vector3d
import org.joml.Vector3dc


/**
 * a convenient class for storing atom information
 *
 * isn't directly used for rendering, so doesn't have to be super efficient
 */
class Atoms private constructor(private val list: MutableList<Atom>): List<Atom> by list {

	constructor (capacity: Int = 16) : this(ArrayList(capacity))

	constructor(other: Atoms) : this(other.size) {
		this.list.addAll(other.list)
	}
	fun copy() = Atoms(this)

	fun add(atom: Atom) = list.add(atom)

	private fun checkIndex(i: Int) {
		if (i < 0 || i >= size) {
			throw IllegalArgumentException("index $i is out of range [0,$size)")
		}
	}
}

data class Atom(
	val element: Element,
	val name: String,
	val pos: Vector3dc
) {

	constructor(element: Element, name: String, x: Double, y: Double, z: Double) :
		this(element, name, Vector3d(x, y, z))

	override fun toString() = name
}
