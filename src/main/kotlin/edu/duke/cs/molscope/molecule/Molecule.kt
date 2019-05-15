package edu.duke.cs.molscope.molecule


class Molecule(
	val name: String,
	val atoms: Atoms,
	val bonds: Bonds
) {

	override fun toString() = name

	constructor(other: Molecule) : this(other.name, other.atoms.copy(), other.bonds.copy())
	fun copy() = Molecule(this)
}
