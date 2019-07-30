package edu.duke.cs.molscope.molecule

import edu.duke.cs.molscope.tools.assert
import org.joml.Vector3d
import java.util.*
import kotlin.collections.ArrayList


/**
 * A collection of atoms (possibly) connected together by covalent bonds.
 *
 * This representation prioritizes flexibility over efficiency.
 */
open class Molecule(
	/** A human-readable description for the molecule. */
	val name: String,
	/** A machine-readable descrition for the molecule, often 3 characters long. */
	val type: String? = null
) {

	// define a companion object so we can extent it
	companion object;

	override fun toString() = name + (type?.let { ": $it" } ?: "")

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

		fun addAll(atoms: Collection<Atom>) {
			for (atom in atoms) {
				add(atom)
			}
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

		fun add(a1: Atom, a2: Atom): Boolean {

			if (a1 === a2) {
				throw IllegalArgumentException("no self bonds allowed")
			}

			// just in case...
			assert { atoms.any { it === a1 } }
			assert { atoms.any { it === a2 } }

			val wasAdded1 = bondedAtoms(a1).add(a2)
			val wasAdded2 = bondedAtoms(a2).add(a1)
			assert(wasAdded1 == wasAdded2) { "bond adjacency table mismatch" }
			return wasAdded1
		}

		fun remove(a1: Atom, a2: Atom): Boolean {
			val wasRemoved1 = bondedAtoms(a1).remove(a2)
			val wasRemoved2 = bondedAtoms(a2).remove(a1)
			assert(wasRemoved1 == wasRemoved2) { "bond adjacency table mismatch" }
			return wasRemoved1
		}

		fun isBonded(a1: Atom, a2: Atom) =
			bondedAtoms(a1).find { it === a2 } != null

		fun clear() {
			adjacency.values.forEach { it.clear() }
		}

		fun count() = adjacency.values.sumBy { it.size }/2

		fun toSet(): Set<Pair<Atom,Atom>> =
			LinkedHashSet<Pair<Atom,Atom>>().apply {
				for (a1 in atoms) {
					for (a2 in bondedAtoms(a1)) {
						// sort the atoms, so we get a determinstic bond ordering
						// and also so both atom directions map to the same bucket in the hash table
						if (a1.hashCode() < a2.hashCode()) {
							add(a1 to a2)
						} else {
							add(a2 to a1)
						}
					}
				}
			}
	}
	val bonds = Bonds()
}

data class Atom(
	val element: Element,
	val name: String,
	val pos: Vector3d
) {

	constructor(element: Element, name: String, x: Double, y: Double, z: Double) :
		this(element, name, Vector3d(x, y, z))

	override fun toString() = name
}
