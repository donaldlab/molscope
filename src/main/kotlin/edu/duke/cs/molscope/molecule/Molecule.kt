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

		fun bondedAtomsSorted(atom: Atom): List<Atom> =
			bondedAtoms(atom).sortedBy { it.name }

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

		fun add(pair: AtomPair) = add(pair.a, pair.b)

		fun remove(a1: Atom, a2: Atom): Boolean {
			val wasRemoved1 = bondedAtoms(a1).remove(a2)
			val wasRemoved2 = bondedAtoms(a2).remove(a1)
			assert(wasRemoved1 == wasRemoved2) { "bond adjacency table mismatch" }
			return wasRemoved1
		}

		fun remove(pair: AtomPair) = remove(pair.a, pair.b)

		fun isBonded(a1: Atom, a2: Atom) =
			bondedAtoms(a1).find { it === a2 } != null

		fun isBonded(pair: AtomPair) = isBonded(pair.a, pair.b)

		fun clear() {
			adjacency.values.forEach { it.clear() }
		}

		fun count() = adjacency.values.sumBy { it.size }/2

		fun toSet(): Set<AtomPair> =
			LinkedHashSet<AtomPair>().apply {
				for (a1 in atoms) {
					// sort the bonded atoms, so the bond list is deterministic
					for (a2 in bondedAtomsSorted(a1)) {
						add(AtomPair(a1, a2))
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


/**
 * A pair of atoms that defines equality by atom identity (ie ===),
 * and is insensitive to atom order.
 */
class AtomPair(val a: Atom, val b: Atom) {

	override fun hashCode() =
		System.identityHashCode(a) xor System.identityHashCode(b)

	override fun equals(other: Any?) =
		other is AtomPair && (
			// use identity comparisons (not equality)
			(this.a === other.a && this.b === other.b)
			// allow the complementary order to match also
			|| (this.a === other.b && this.b === other.a)
		)

	override fun toString() = "$a - $b"

	operator fun component1() = a
	operator fun component2() = b

	fun toContent() = ContentAtomPair(a, b)
}


/**
 * A pair of atoms that defines equality by atom equality (ie ==),
 * and is insensitive to atom order.
 */
class ContentAtomPair(val a: Atom, val b: Atom) {

	override fun hashCode() =
		a.hashCode() xor b.hashCode()

	override fun equals(other: Any?) =
		other is ContentAtomPair && (
			// use equality comparisons (not identity)
			(this.a == other.a && this.b == other.b)
			// allow the complementary order to match also
			|| (this.a == other.b && this.b == other.a)
		)

	override fun toString() = "$a - $b"

	operator fun component1() = a
	operator fun component2() = b

	fun toIdentity() = AtomPair(a, b)
}


/**
 * Combine multiple Molecules into a single Molecule (by making copies of the input molecules)
 * and returns a map of the input atoms to the atoms in the combined molecule
 */
fun Collection<Molecule>.combine(name: String, resolveChainIds: Boolean = false): Pair<Molecule,Map<Atom,Atom>> {

	// are there any polymers?
	val dstMol = if (any { it is Polymer }) {

		// yup, the out molecules needs to be a polymer too
		Polymer(name)
	} else {
		Molecule(name)
	}

	// copy atoms
	val atomMap = IdentityHashMap<Atom,Atom>()
	for (srcMol in this) {
		for (srcAtom in srcMol.atoms) {
			val dstAtom = srcAtom.copy()
			atomMap[srcAtom] = dstAtom
			dstMol.atoms.add(dstAtom)
		}
	}

	// copy bonds
	for (srcMol in this) {
		for (srcBond in srcMol.bonds.toSet()) {
			dstMol.bonds.add(
				atomMap.getValue(srcBond.a),
				atomMap.getValue(srcBond.b)
			)
		}
	}

	// make a unique chain id generator
	val usedChainIds =
		filterIsInstance<Polymer>().flatMap { polymer -> polymer.chains.map { it.id } }
			.toMutableSet()
	var nextChainId = 'A'
	fun getNextChainId(): String {
		if (nextChainId > 'Z') {
			throw IllegalStateException("out of unique chain ids in A-Z")
		}
		return "${nextChainId++}"
	}
	fun getUniqueChainId(): String {
		var chainId = getNextChainId()
		while (chainId in usedChainIds) {
			chainId = getNextChainId()
		}
		return chainId
	}

	// copy the chains, if any
	for (srcMol in filterIsInstance<Polymer>()) {
		for (srcChain in srcMol.chains) {

			var dstChainId = srcChain.id
			if ((dstMol as Polymer).chains.any { it.id == dstChainId }) {
				if (resolveChainIds) {
					dstChainId = getUniqueChainId()
				} else {
					throw IllegalArgumentException("molecules have clashing chainIds, and resolveChainIds is false")
				}
			}

			val dstChain = Polymer.Chain(dstChainId)
			dstMol.chains.add(dstChain)

			for (srcRes in srcChain.residues) {
				dstChain.residues.add(Polymer.Residue(
					srcRes.id,
					srcRes.type,
					srcRes.atoms.map { atomMap.getValue(it) }
				))
			}
		}
	}

	return dstMol to atomMap
}
