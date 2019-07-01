package edu.duke.cs.molscope.molecule

import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.*
import kotlin.collections.ArrayList


/**
 * A collection of atoms (possibly) connected together by covalent bonds.
 *
 * This representation prioritizes flexibility over efficiency.
 */
open class Molecule(val name: String) {

	// define a companion object so we can extent it
	companion object;

	override fun toString() = name

	open fun copy() = Molecule(name).apply {
		val src = this@Molecule
		val dst = this@apply
		src.copyTo(dst)
	}

	fun copyTo(dst: Molecule): Map<Atom,Atom> {
		val src = this

		// copy the atoms
		val atomMap = src.atoms.list.associate { it to it.copy() }
		dst.atoms.list.addAll(atomMap.values)

		// copy the bonds
		for ((otherAtom, otherBondedAtoms) in src.bonds.adjacency) {
			val atom = atomMap[otherAtom]!!
			for (otherBondedAtom in otherBondedAtoms) {
				val bondedAtom = atomMap[otherBondedAtom]!!
				dst.bonds.add(atom, bondedAtom)
			}
		}

		return atomMap
	}


	inner class Atoms internal constructor(internal val list: MutableList<Atom> = ArrayList()) : List<Atom> by list {

		fun add(atom: Atom): Atom {
			list.add(atom)
			return atom
		}

		fun remove(atom: Atom) {

			list.remove(atom)

			bonds.adjacency.remove(atom)
			for (bondedAtoms in bonds.adjacency.values) {
				bondedAtoms.remove(atom)
			}
		}
	}
	val atoms = Atoms()


	inner class Bonds internal constructor() {

		// represent the bonds as a doubly-linked adjacency list
		internal val adjacency = IdentityHashMap<Atom,MutableSet<Atom>>()

		fun bondedAtoms(atom: Atom): MutableSet<Atom> =
			adjacency.computeIfAbsent(atom) { Collections.newSetFromMap(IdentityHashMap()) }

		fun add(a1: Atom, a2: Atom) {

			if (a1 === a2) {
				throw IllegalArgumentException("no self bonds allowed")
			}

			bondedAtoms(a1).add(a2)
			bondedAtoms(a2).add(a1)
		}

		fun remove(a1: Atom, a2: Atom) {
			bondedAtoms(a1).remove(a2)
			bondedAtoms(a2).remove(a1)
		}

		fun isBonded(a1: Atom, a2: Atom) =
			a1 in bondedAtoms(a2)

		fun clear() {
			adjacency.values.forEach { it.clear() }
		}
	}
	val bonds = Bonds()
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
