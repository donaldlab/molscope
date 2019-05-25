package edu.duke.cs.molscope.molecule


/**
 * An extension to Molecule that supports residues in a chain topology
 */
class Polymer(
	name: String
): Molecule(name) {

	val chain: MutableList<Residue> get() = _chain
	private val _chain = ArrayList<Residue>()

	inner class Residue(
		val id: String,
		val atomIndices: List<Int>
	) {

		constructor(id: String, atomIndices: IntRange) : this(id, atomIndices.toList())

		val atoms: Iterable<Atom> get() = atomIndices.map { this@Polymer.atoms[it] }
	}
}
