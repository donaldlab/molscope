package edu.duke.cs.molscope.molecule


/**
 * A collection of atoms connected together by covalent bonds.
 *
 * All atoms in the molecule should be connected into a single component.
 */
open class Molecule(
	val name: String,
	val atoms: Atoms,
	val bonds: Bonds
) {

	override fun toString() = name

	constructor(name: String) : this(name, Atoms(), Bonds())
	constructor(other: Molecule) : this(other.name, other.atoms.copy(), other.bonds.copy())
	fun copy() = Molecule(this)
}
