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

		fun findOrThrow(name: String) =
			list
				.find { it.name == name }
				?: throw NoSuchElementException("no atom with name $name")
	}
	val atoms = Atoms()


	inner class Bonds internal constructor() {

		// represent the bonds as a doubly-linked adjacency list
		internal val adjacency = IdentityHashMap<Atom,MutableSet<Atom>>()

		fun bondedAtoms(atom: Atom): MutableSet<Atom> =
			adjacency.getOrPut(atom) { Atom.identitySet() }

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

	/**
	 * Crawl the bond network in depth-first order starting at the source atom.
	 */
	fun dfs(
		source: Atom,
		visitSource: Boolean = false,
		shouldVisit: (fromAtom: Atom, toAtom: Atom, dist: Int) -> Boolean = { _, _, _ -> true }
	) = search(source, visitSource, shouldVisit, SearchType.DFS)

	/**
	 * Crawl the bond network in breadth-first order starting at the source atom.
	 */
	fun bfs(
		source: Atom,
		visitSource: Boolean = false,
		shouldVisit: (fromAtom: Atom, toAtom: Atom, dist: Int) -> Boolean = { _, _, _ -> true }
	) = search(source, visitSource, shouldVisit, SearchType.BFS)

	data class Searched(val atom: Atom, val dist: Int)

	private enum class SearchType {
		DFS,
		BFS
	}

	private fun search(
		source: Atom,
		visitSource: Boolean = false,
		shouldVisit: (fromAtom: Atom, toAtom: Atom, dist: Int) -> Boolean = { _, _, _ -> true },
		searchType: SearchType
	) = object : Iterable<Searched> {

		override fun iterator() = object : Iterator<Searched> {

			// track the atom visitation schedule
			val toVisit = ArrayDeque<Searched>()
			val visitScheduled = Atom.identitySet()

			fun scheduleVisit(atom: Atom, dist: Int) {
				toVisit.add(Searched(atom, dist))
				visitScheduled.add(atom)
			}

			override fun hasNext() = toVisit.isNotEmpty()

			override fun next(): Searched {

				// take the next step
				val step = when (searchType) {
					SearchType.DFS -> toVisit.pollLast()
					SearchType.BFS -> toVisit.pollFirst()
				} ?: throw NoSuchElementException("no more atoms to visit")

				// schedule visits to neighbors
				// NOTE: use the sorted atoms, so search order is deterministic
				val nextDist = step.dist + 1
				val bondedAtoms = when (searchType) {
					// reverse the DFS neighbor order, so the pollLast() grabs in forward order
					SearchType.DFS -> bonds.bondedAtomsSorted(step.atom).reversed()
					SearchType.BFS -> bonds.bondedAtomsSorted(step.atom)
				}
				for (bondedAtom in bondedAtoms) {
					if (bondedAtom !in visitScheduled && shouldVisit(step.atom, bondedAtom, nextDist)) {
						scheduleVisit(bondedAtom, nextDist)
					}
				}

				return step
			}

			init {

				// seed with the source atom
				scheduleVisit(source, 0)

				// skip the source atom, if need
				if (!visitSource) {
					next()
				}
			}
		}
	}
}

data class Atom(
	val element: Element,
	val name: String,
	val pos: Vector3d
) {

	constructor(element: Element, name: String, x: Double, y: Double, z: Double) :
		this(element, name, Vector3d(x, y, z))

	override fun toString() = name

	companion object {

		/**
		 * Creates a map that compares atoms using === rather than ==/equals()
		 */
		fun <T> identityMap(): MutableMap<Atom,T> =
			IdentityHashMap<Atom,T>()

		/**
		 * Creates a set that compares atoms using === rather than ==/equals()
		 */
		fun identitySet(): MutableSet<Atom> =
			Collections.newSetFromMap(identityMap<Boolean>())

		fun identitySetOf(vararg atoms: Atom): MutableSet<Atom> =
			identitySet().apply {
				addAll(atoms)
			}
	}
}

fun Collection<Atom>.toIdentitySet() =
	Atom.identitySet().apply {
		addAll(this@toIdentitySet)
	}

fun Set<Atom>.union(other: Set<Atom>) =
	Atom.identitySet().apply {
		addAll(this@union)
		addAll(other)
	}

fun Set<Atom>.intersection(other: Set<Atom>) =
	Atom.identitySet().apply {
		addAll(this@intersection)
		retainAll(other)
	}

fun Iterable<Set<Atom>>.union() =
	Atom.identitySet().apply {
		for (set in this@union) {
			addAll(set)
		}
	}

fun Iterable<Set<Atom>>.intersection() =
	Atom.identitySet().apply {
		val list = this@intersection.toList()
		if (list.isNotEmpty()) {
			addAll(list[0])
			for (i in 1 until list.size) {
				retainAll(list[i])
			}
		}
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
 * Creates unique chain ids to solve chain id conflicts when combining molecules into Polymers.
 */
interface ChainIdGenerator {

	fun setUsedIds(ids: Collection<String>)
	fun generateId(): String
}

/**
 * Generates chains for non-polymer molecules when combining with polymers.
 */
interface ChainGenerator {

	fun setUsedIds(ids: Collection<String>)

	/**
	 * `nonPolymerMol` is the input molecule to combination. `polyerAtoms` are the
	 * atoms in the combined Polymer output molecule, which have already been copied from
	 * `nonPolymerMol`.
	 */
	fun generateChain(nonPolymerMol: Molecule, polymerAtoms: List<Atom>): Polymer.Chain
}

/**
 * Combine multiple Molecules into a single Molecule (by making copies of the input molecules)
 * and returns a map between the input atoms (A side) and the atoms in the combined molecule (B side).
 *
 * If one of the input molecules is a polymer, the output molecule will be a Polymer.
 *
 * Include a ChainGenerator if you want any non-polymer molecules to be combined into the Polymer as new chains.
 */
fun Collection<Molecule>.combine(
	name: String,
	chainIdGenerator: ChainIdGenerator? = null,
	chainGenerator: ChainGenerator? = null
): Pair<Molecule,AtomMap> {

	// are there any polymers?
	val dstMol = if (any { it is Polymer }) {

		// yup, the out molecules needs to be a polymer too
		Polymer(name)
	} else {
		Molecule(name)
	}

	// copy atoms
	val atomMap = AtomMap()
	for (srcMol in this) {
		for (srcAtom in srcMol.atoms) {
			val dstAtom = srcAtom.copy()
			atomMap.add(srcAtom, dstAtom)
			dstMol.atoms.add(dstAtom)
		}
	}

	// copy bonds
	for (srcMol in this) {
		for (srcBond in srcMol.bonds.toSet()) {
			dstMol.bonds.add(
				atomMap.getBOrThrow(srcBond.a),
				atomMap.getBOrThrow(srcBond.b)
			)
		}
	}

	// prep the chain id generator
	val srcPolymers = filterIsInstance<Polymer>()
	chainIdGenerator?.setUsedIds(srcPolymers.flatMap { it.chains.map { it.id }})

	// copy the chains, if any
	for (srcMol in srcPolymers) {
		dstMol as Polymer
		for (srcChain in srcMol.chains) {

			var dstChainId = srcChain.id
			if (dstMol.chains.any { it.id == dstChainId }) {
				dstChainId = chainIdGenerator?.generateId()
					?: throw IllegalArgumentException(
						"chain id $dstChainId clashes with existing chain ids ${dstMol.chains.map { it.id }},"
						+ "and no chain id resolver was provided"
					)
			}

			val dstChain = Polymer.Chain(dstChainId)
			dstMol.chains.add(dstChain)

			for (srcRes in srcChain.residues) {
				dstChain.residues.add(Polymer.Residue(
					srcRes.id,
					srcRes.type,
					srcRes.atoms.map { atomMap.getBOrThrow(it) }
				))
			}
		}
	}

	// generate chains for any non-polymer molecules if needed
	if (dstMol is Polymer && chainGenerator != null) {

		// prep the chain id generator inside the chain generator
		chainGenerator.setUsedIds(dstMol.chains.map { it.id })

		for (srcMol in filter { it !is Polymer }) {
			dstMol.chains.add(chainGenerator.generateChain(
				srcMol,
				srcMol.atoms.map { atomMap.getBOrThrow(it) }
			))
		}
	}

	return dstMol to atomMap
}
