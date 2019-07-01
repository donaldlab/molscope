package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.ByteFlags
import cuchaz.kludge.tools.put
import cuchaz.kludge.vulkan.ClearValue
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.molecule.MoleculeSelector
import java.nio.ByteBuffer


data class RenderEffect(val flags: ByteFlags<Flags>, val r: UByte, val g: UByte, val b: UByte) {

	enum class Flags(override val value: Byte) : ByteFlags.Bit {

		// these must match the render effects in post.frag

		/** shows the selected items in brighter colors */
		Highlight(1 shl 0),

		/** draws an inset border around the selected items */
		Inset(1 shl 1),

		/** draws an outset border around the selected items */
		Outset(1 shl 2)
	}

	companion object {
		val clearColor = ClearValue.Color.Int(0, 0, 0, 0)
	}
}

fun ByteBuffer.put(effect: RenderEffect?) {
	if (effect != null) {
		put(effect.r)
		put(effect.g)
		put(effect.b)
		put(effect.flags.value)
	} else {
		putInt(0)
	}
}

class MoleculeRenderEffects(val mol: Molecule) {

	private val effectsByAtom = HashMap<Atom,RenderEffect>()

	var sequence: Int = 0
		private set

	fun clear() {
		if (effectsByAtom.isNotEmpty()) {
			effectsByAtom.clear()
			sequence += 1
		}
	}

	operator fun set(atom: Atom, effect: RenderEffect) {
		if (effectsByAtom[atom] != effect) {
			effectsByAtom[atom] = effect
			sequence += 1
		}
	}

	operator fun set(atoms: Collection<Atom>, effect: RenderEffect) {
		var isChanged = false
		for (atom in atoms) {
			if (effectsByAtom[atom] != effect) {
				effectsByAtom[atom] = effect
				isChanged = true
			}
		}
		if (isChanged) {
			sequence += 1
		}
	}

	fun remove(atom: Atom) {
		val oldVal = effectsByAtom.remove(atom)
		if (oldVal != null) {
			sequence += 1
		}
	}

	fun remove(atoms: Collection<Atom>) {
		var isChanged = false
		for (atom in atoms) {
			val oldVal = effectsByAtom.remove(atom)
			if (oldVal != null) {
				isChanged = true
			}
		}
		if (isChanged) {
			sequence += 1
		}
	}

	operator fun set(selector: MoleculeSelector, effect: RenderEffect) {
		set(selector(mol), effect)
	}

	fun remove(selector: MoleculeSelector) {
		remove(selector(mol))
	}

	operator fun get(atom: Atom): RenderEffect? = effectsByAtom[atom]
}