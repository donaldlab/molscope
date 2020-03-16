package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.*
import edu.duke.cs.molscope.render.MoleculeRenderEffects
import edu.duke.cs.molscope.render.SphereRenderable
import edu.duke.cs.molscope.render.put
import org.joml.AABBf
import java.nio.ByteBuffer


/**
 * views a molecule using the space-filling sphere convention
 */
// TODO: optimize molecule transformations so we don't have to re-create the whole view for a large molecule?
class SpaceFilling(
	override val mol: Molecule,
	initialSelector: MoleculeSelector = MoleculeSelectors.all
): MoleculeRenderView {

	override var selector = initialSelector
		set(value) {
			field = value
			moleculeChanged()
		}

	override var isVisible = true

	var sel = selector(mol)
		private set

	private var molSequence = 0
	override fun moleculeChanged() {
		sel = selector(mol)
		molSequence += 1
	}

	override var renderEffects = MoleculeRenderEffects(mol)

	override val spheres = object : SphereRenderable {
		
		override val numVertices get() = sel.size*4
		override val verticesSequence get() = molSequence + renderEffects.sequence

		override fun fillVertexBuffer(buf: ByteBuffer, colorsMode: ColorsMode) {

			sel.forEachIndexed { atomIndex, atom ->

				// write all vertex data 4 times
				for (i in 0 until 4) {

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
					buf.put(renderEffects[atom])
				}
			}
		}

		override val boundingBox get() = calcBoundingBox()

		override val numOccluders get() = sel.size

		override fun fillOcclusionBuffer(buf: ByteBuffer) {

			for (atom in sel) {

				// downgrade atom pos to floats for rendering
				buf.putFloat(atom.pos.x.toFloat())
				buf.putFloat(atom.pos.y.toFloat())
				buf.putFloat(atom.pos.z.toFloat())

				ElementProps[atom].apply {
					buf.putFloat(radius)
				}
			}
		}
	}

	override fun calcBoundingBox() =
		AABBf().apply {
			sel.forEachIndexed { i, atom ->

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

	override fun getIndexed(index: Int) = sel.getOrNull(index)
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
					Element.Sulfur -> ElementProps(1.8f, ColorPalette.yellow)
					Element.Phosphorus -> ElementProps(1.8f, ColorPalette.orange)
					else -> ElementProps(2.0f, ColorPalette.darkGrey)
				}
		}
	}
}
