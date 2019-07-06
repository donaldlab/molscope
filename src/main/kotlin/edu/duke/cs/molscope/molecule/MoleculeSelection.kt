package edu.duke.cs.molscope.molecule


/** A function that returns a subset of atoms in a molecule */
typealias MoleculeSelector = (Molecule) -> List<Atom>

object MoleculeSelectors {

	/** Selects all the atoms */
	val all: MoleculeSelector = { mol -> mol.atoms }

	/**
	 * If the molecule is a polymer, the selection returns the mainchain atoms.
	 * Otherwise, the selections returns no atoms.
	 */
	val mainchain: MoleculeSelector = { mol ->
		if (mol is Polymer) {
			// TODO: try to find the backbone using the bond network
			throw Error("not implemented yet")
		} else {
			emptyList()
		}
	}

	fun atomsByName(name: String): MoleculeSelector = { mol ->
		fun String.normalize() = toUpperCase()
		mol.atoms.filter { it.name.normalize() == name.normalize() }
	}
}
