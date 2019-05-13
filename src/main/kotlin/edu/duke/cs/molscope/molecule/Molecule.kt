package edu.duke.cs.molscope.molecule


class Molecule(
	val name: String,
	val atoms: Atoms,
	val bonds: Bonds
) {

	override fun toString() = name
}
