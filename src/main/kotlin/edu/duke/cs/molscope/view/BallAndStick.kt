package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.*
import edu.duke.cs.molscope.render.CylinderRenderable
import edu.duke.cs.molscope.render.MoleculeRenderEffects
import edu.duke.cs.molscope.render.SphereRenderable
import edu.duke.cs.molscope.render.put
import org.joml.AABBf
import java.nio.ByteBuffer


/**
 * views a molecule using the ball and stick convention
 */
class BallAndStick(
	override val mol: Molecule,
	val selector: MoleculeSelector = MoleculeSelectors.all
): MoleculeRenderView {

	var sel = selector(mol)
		private set

	private var molSequence = 0
	override fun moleculeChanged() {
		sel = selector(mol)
		updateBonds()
		molSequence += 1
	}

	override var renderEffects = MoleculeRenderEffects(mol)

	// copy all the bonds (in the selection) as a list
	data class Bond(val i1: Int, val i2: Int)
	private val bonds = HashSet<Bond>()

	private fun updateBonds() {
		bonds.clear()
		sel.forEachIndexed { i1, a1 ->
			for (a2 in mol.bonds.bondedAtoms(a1)) {
				val i2 = sel.indexOf(a2)
				if (i2 < 0) {
					continue
				}

				// sort the indices to normalize the bond
				bonds.add(if (i1 < i2) {
					Bond(i1, i2)
				} else {
					Bond(i2, i1)
				})
			}
		}
	}


	// render the atoms as spheres
	internal val sphereRenderable = object : SphereRenderable {

		override val numVertices get() = sel.size
		override val verticesSequence get() = molSequence + renderEffects.sequence

		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {

			sel.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x().toFloat())
				buf.putFloat(atom.pos.y().toFloat())
				buf.putFloat(atom.pos.z().toFloat())

				buf.putFloat(atomRadius)
				ElementProps[atom].apply {
					buf.putColor4Bytes(color[colorsMode])
				}

				// TODO: allow different indexing strategies (eg residue, molecule)
				buf.putInt(atomIndex)
				buf.put(renderEffects[atom])
			}
		}

		override val boundingBox get() = calcBoundingBox()

		override fun fillOcclusionBuffer(buf: ByteBuffer) {

			for (atom in sel) {

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x.toFloat())
				buf.putFloat(atom.pos.y.toFloat())
				buf.putFloat(atom.pos.z.toFloat())
				buf.putFloat(atomRadius)
			}
		}
	}

	// render the bonds as cylinders
	internal val cylinderRenderable = object : CylinderRenderable {

		override val numVertices get() = sel.size
		override val verticesSequence get() = molSequence + renderEffects.sequence
		override val indicesSequence get() = molSequence

		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {
			sel.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x().toFloat())
				buf.putFloat(atom.pos.y().toFloat())
				buf.putFloat(atom.pos.z().toFloat())

				buf.putFloat(bondRadius)
				buf.putColor4Bytes(ElementProps[atom].color[colorsMode])

				// TODO: allow different indexing strategies (eg residue, molecule)
				buf.putInt(atomIndex)
				buf.put(renderEffects[atom])
			}
		}

		override val numIndices get() = bonds.size*2

		override fun fillIndexBuffer(buf: ByteBuffer) {
			for (bond in bonds) {
				buf.putInt(bond.i1)
				buf.putInt(bond.i2)
			}
		}

		override val boundingBox get() = calcBoundingBox()

		override fun fillOcclusionBuffer(buf: ByteBuffer) {
			for (bond in bonds) {
				val atom1 = sel[bond.i1]
				val atom2 = sel[bond.i2]

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom1.pos.x().toFloat())
				buf.putFloat(atom1.pos.y().toFloat())
				buf.putFloat(atom1.pos.z().toFloat())
				buf.putFloat(0f) // padding
				buf.putFloat(atom2.pos.x().toFloat())
				buf.putFloat(atom2.pos.y().toFloat())
				buf.putFloat(atom2.pos.z().toFloat())
				buf.putFloat(bondRadius)
			}
		}
	}

	override fun calcBoundingBox() =
		AABBf().apply {

			val r = atomRadius

			sel.forEachIndexed { i, atom ->

				val x = atom.pos.x.toFloat()
				val y = atom.pos.y.toFloat()
				val z = atom.pos.z.toFloat()

				if (i == 0) {
					setMin(x - r, y - r, z - r)
					setMax(x + r, y + r, z + r)
				} else {
					expandToInclude(x - r, y - r, z - r)
					expandToInclude(x + r, y + r, z + r)
				}
			}
		}


	override fun getIndexed(index: Int) = sel.getOrNull(index)
	// TODO: allow indexing other things?


	companion object {

		// TODO: make these parameters
		private const val atomRadius = 0.2f
		private const val bondRadius = 0.2f
	}

	// TODO: allow overriding these in constructor args
	private data class ElementProps(
		val color: Color
	) {

		companion object {

			operator fun get(atom: Atom) =
				when (atom.element) {
					Element.Hydrogen -> ElementProps(ColorPalette.lightGrey)
					Element.Carbon -> ElementProps(ColorPalette.darkGrey)
					Element.Nitrogen -> ElementProps(ColorPalette.blue)
					Element.Oxygen -> ElementProps(ColorPalette.red)
					Element.Sulfur -> ElementProps(ColorPalette.yellow)
					else -> ElementProps(ColorPalette.darkGrey)
				}
		}
	}
}
