package edu.duke.cs.molscope.molecule


/**
 * An extension to Molecule that supports residues in a chain topology,
 * and separates mainchain from sidechain
 */
class Polymer(
	name: String
) : Molecule(name) {

	class Residue(
		val id: String,
		val type: String,
		val mainchain: List<Atom>,
		val sidechains: List<List<Atom>>
	)

	class Chain(val id: String) {
		val residues: MutableList<Residue> = ArrayList()
	}

	val chains: MutableList<Chain> = ArrayList()

	override fun copy() = Polymer(name).apply {
		val src = this@Polymer
		val dst = this@apply
		src.copyTo(dst)
	}

	fun copyTo(dst: Polymer) {
		val src = this

		val atomMap = src.copyTo(dst as Molecule)

		// copy all the chains
		for (srcChain in src.chains) {
			val dstChain = Chain(srcChain.id)
			for (srcRes in srcChain.residues) {
				dstChain.residues.add(Residue(
					srcRes.id,
					srcRes.type,
					srcRes.mainchain.map { atomMap[it]!! },
					srcRes.sidechains.map { it.map { atomMap[it]!! } }
				))
			}
			dst.chains.add(dstChain)
		}
	}
}
