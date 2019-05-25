package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Element
import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.render.SphereRenderable
import org.joml.AABBf
import java.nio.ByteBuffer


/**
 * views a molecule using the space-filling sphere convention
 */
// TODO: optimize molecule transformations so we don't have to re-create the whole view for a large molecule?
class SpaceFilling(
	mol: Molecule
	// TODO: molecule subset selection?
): RenderView {

	// make a copy of the atoms
	private val atoms = mol.atoms.copy()

	internal val sphereRenderable = object : SphereRenderable {
		
		override val numVertices = mol.atoms.size
		
		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {

			mol.atoms.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x.toFloat())
				buf.putFloat(atom.pos.y.toFloat())
				buf.putFloat(atom.pos.z.toFloat())

				ElementProps[atom].apply {
					buf.putFloat(radius)
					buf.putColor4Bytes(color[colorsMode])
				}

				// TODO: allow different indexing strategies (eg residue, molecule)
				buf.putInt(atomIndex)
			}
		}
	}

	override fun calcBoundingBox() =
		AABBf().apply {
			atoms.forEachIndexed { i, atom ->

				val x = atom.pos.x.toFloat()
				val y = atom.pos.y.toFloat()
				val z = atom.pos.z.toFloat()
				val r = ElementProps[atom].radius

				if (i == 0) {
					setMin(x - r, y - r, z - r)
					setMax(x + r, y + r, z + r)
				} else {
					expandToInclude(x - r, y - r, z - r)
					expandToInclude(x + r, y + r, z + r)
				}
			}
		}

	override fun getIndexed(index: Int) = atoms.getOrNull(index)
	// TODO: allow indexing other things?


	// TODO: allow overriding these in constructor args
	private data class ElementProps(
		val radius: Float,
		val color: Color
	) {

		companion object {

			operator fun get(atom: Atom) =
				when (atom.element) {
					Element.Hydrogen -> ElementProps(1f, ColorPalette.lightGrey)
					Element.Carbon -> ElementProps(1.75f, ColorPalette.darkGrey)
					Element.Nitrogen -> ElementProps(1.55f, ColorPalette.blue)
					Element.Oxygen -> ElementProps(1.4f, ColorPalette.red)
				}
		}
	}
}
