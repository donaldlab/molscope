package edu.duke.cs.molscope.molecule

import org.joml.Vector3d
import org.joml.Vector3dc


/**
 * an efficient class for storing atom information
 *
 * tries to avoid allocating memory for each atom individually,
 * which optimizes layout for rendering
 *
 * yet allows convenient access to individual atoms and their properties
 */
class Atoms(capacity: Int = 16): Iterable<Atom> {

	var size: Int = 0
		private set

	private val elements = ArrayList<Element>(capacity)
	private val names = ArrayList<String>(capacity)
	private val coords = ArrayList<Double>(capacity*3)

	fun add(atom: Atom) {
		elements.add(atom.element)
		names.add(atom.name)
		coords.add(atom.pos.x())
		coords.add(atom.pos.y())
		coords.add(atom.pos.z())
		size += 1
	}

	override fun iterator() =
		object : Iterator<Atom> {

			private var i = 0

			override fun next() =
				get(i)
				.also { i += 1 }

			override fun hasNext() = i < size
		}

	private fun checkIndex(i: Int) {
		if (i < 0 || i >= size) {
			throw IllegalArgumentException("index $i is out of range [0,$size)")
		}
	}

	operator fun get(i: Int): Atom {
		checkIndex(i)
		return Atom(
			elements[i],
			names[i],
			Vector3d(coords[i*3], coords[i*3 + 1], coords[i*3 + 2])
		)
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
