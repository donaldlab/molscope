package edu.duke.cs.molscope.molecule

import java.util.*


/**
 * A bijection between two sets of atoms.
 */
class AtomMap {

	private val a2b = Atom.mapIdentity<Atom>()
	private val b2a = Atom.mapIdentity<Atom>()

	fun add(a: Atom, b: Atom) {
		a2b[a] = b
		b2a[b] = a
	}

	fun addAll(other: AtomMap) {
		a2b.putAll(other.a2b)
		b2a.putAll(other.b2a)
	}

	fun getB(a: Atom) = a2b[a]
	fun getA(b: Atom) = b2a[b]

	fun getBOrThrow(a: Atom) =
		getB(a) ?: throw NoSuchElementException("atom $a is not in the A side")
	fun getAOrThrow(b: Atom) =
		getA(b) ?: throw NoSuchElementException("atom $b is not in the B side")

	fun removeA(a: Atom) {
		val b = getBOrThrow(a)
		a2b.remove(a)
		b2a.remove(b)
	}

	fun removeB(b: Atom) {
		val a = getAOrThrow(b)
		b2a.remove(b)
		a2b.remove(a)
	}

	companion object {

		fun identity(atoms: Collection<Atom>) =
			AtomMap().apply {
				for (atom in atoms) {
					add(atom, atom)
				}
			}
	}
}
