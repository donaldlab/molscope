package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Element
import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.render.CylinderRenderable
import edu.duke.cs.molscope.render.SphereRenderable
import org.joml.AABBf
import java.nio.ByteBuffer


/**
 * views a molecule using the ball and stick convention
 */
class BallAndStick(
	mol: Molecule
	// TODO: molecule subset selection?
): RenderView {

	// render the atoms as spheres
	internal val sphereRenderable = object : SphereRenderable {

		override val numVertices = mol.atoms.size

		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {

			mol.atoms.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x().toFloat())
				buf.putFloat(atom.pos.y().toFloat())
				buf.putFloat(atom.pos.z().toFloat())

				ElementProps[atom].apply {
					buf.putFloat(atomRadius)
					buf.putColor4Bytes(color[colorsMode])
				}

				// TODO: allow different indexing strategies (eg residue, molecule)
				buf.putInt(atomIndex)
			}
		}
	}

	// render the bonds as cylinders
	internal val cylinderRenderable = object : CylinderRenderable {

		override val numVertices = mol.atoms.size

		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {
			mol.atoms.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x().toFloat())
				buf.putFloat(atom.pos.y().toFloat())
				buf.putFloat(atom.pos.z().toFloat())

				buf.putFloat(bondRadius)
				buf.putColor4Bytes(ElementProps[atom].color[colorsMode])

				// TODO: allow different indexing strategies (eg residue, molecule)
				buf.putInt(atomIndex)
			}
		}

		override val numIndices = mol.bonds.size*2

		override fun fillIndexBuffer(buf: ByteBuffer) {
			for (bond in mol.bonds) {
				buf.putInt(bond.i1)
				buf.putInt(bond.i2)
			}
		}
	}

	override fun calcBoundingBox() =
		AABBf().apply {

			val r = atomRadius

			atoms.forEachIndexed { i, atom ->

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


	// save atom for index lookups
	private val atoms = mol.atoms.copy()

	override fun getIndexed(index: Int) = atoms.getOrNull(index)
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
				}
		}
	}
}
