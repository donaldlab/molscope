package edu.duke.cs.molscope.molecule


/**
 * An extension to Molecule that supports residues in a chain topology,
 * and separates mainchain from sidechain
 */
class Polymer(
	name: String
) : Molecule(name) {

	val chain: MutableList<Residue> get() = _chain
	private val _chain = ArrayList<Residue>()

	inner class Residue(
		val id: String,
		val mainchain: List<Atom>,
		val sidechains: List<List<Atom>>
	)

	override fun copy() = Polymer(name).apply {
		val src = this@Polymer
		val dst = this@apply
		src.copyTo(dst)
	}

	fun copyTo(dst: Polymer) {
		val src = this

		val atomMap = src.copyTo(dst as Molecule)

		// copy all the residues
		for (res in src.chain) {
			dst.chain.add(Residue(
				res.id,
				res.mainchain.map { atomMap[it]!! },
				res.sidechains.map { it.map { atomMap[it]!! } }
			))
		}
	}
}
