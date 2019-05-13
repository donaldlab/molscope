package edu.duke.cs.molscope.molecule

import org.joml.Vector3d


// TODO: convert to kotlin test
fun main() {

	val atoms = Atoms().apply {
		add(Atom(Element.Hydrogen, "H1", 1.0, 2.0, 3.0))
		add(Atom(Element.Carbon, "Ca", 4.2, 3.7, 5.9))
	}

	assert(atoms.size == 2)
	atoms[0].apply {
		assert(name == "H1")
		assert(element == Element.Hydrogen)
		assert(pos == Vector3d(1.0, 2.0, 3.0))
	}
	atoms[1].apply {
		assert(name == "Ca")
		assert(element == Element.Carbon)
		assert(pos == Vector3d(4.2, 3.7, 5.9))
	}

	val iter = atoms.iterator()
	assert(iter.hasNext())
	assert(iter.next().name == "H1")
	assert(iter.hasNext())
	assert(iter.next().name == "Ca")
	assert(!iter.hasNext())
}
