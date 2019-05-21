package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.expandToInclude
import cuchaz.kludge.tools.skip
import cuchaz.kludge.vulkan.ColorRGBA
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Element
import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.render.SphereRenderable
import edu.duke.cs.molscope.render.SphereRenderer
import org.joml.AABBf
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * create a view of the molecule at this point in time
 */
// TODO: optimize molecule transformations so we don't have to re-create the whole view for a large molecule?
class SpaceFilling(
	mol: Molecule
	// TODO: molecule subset selection?
): RenderView {
	
	internal val sphereRenderable = object : SphereRenderable {
		
		override val numSpheres = mol.atoms.size
		
		override val vertexBuf =
			ByteBuffer.allocate(SphereRenderer.vertexInput.size.toInt()*numSpheres).apply {

			// use native byte ordering so we can efficiently copy to the GPU
			order(ByteOrder.nativeOrder())

			mol.atoms.forEachIndexed { atomIndex, atom ->

				// downgrade atom pos to floats for rendering
				putFloat(atom.pos.x().toFloat())
				putFloat(atom.pos.y().toFloat())
				putFloat(atom.pos.z().toFloat())

				ElementProps[atom].apply {
					putFloat(radius)
					putColor4Bytes(color)
				}

				// TODO: allow different indexing strategies (eg residue, molecule)
				putInt(atomIndex)
			}
			flip()
		}
	}

	override fun calcBoundingBox() =
		AABBf().apply {

			sphereRenderable.vertexBuf.rewind()
			for (i in 0 until sphereRenderable.numSpheres) {

				val x = sphereRenderable.vertexBuf.float
				val y = sphereRenderable.vertexBuf.float
				val z = sphereRenderable.vertexBuf.float
				val r = sphereRenderable.vertexBuf.float
				sphereRenderable.vertexBuf.skip(8)

				if (i == 0) {
					setMin(x - r, y - r, z - r)
					setMax(x + r, y + r, z + r)
				} else {
					expandToInclude(x - r, y - r, z - r)
					expandToInclude(x + r, y + r, z + r)
				}
			}
			sphereRenderable.vertexBuf.rewind()
		}

	private data class ElementProps(
		val radius: Float,
		val color: ColorRGBA.Int
	) {

		companion object {

			operator fun get(atom: Atom) =
				when (atom.element) {
					Element.Hydrogen -> ElementProps(1f, ColorRGBA.Int(200, 200, 200))
					Element.Carbon -> ElementProps(1.75f, ColorRGBA.Int(60, 60, 60))
					Element.Nitrogen -> ElementProps(1.55f, ColorRGBA.Int(20, 20, 200))
					Element.Oxygen -> ElementProps(1.4f, ColorRGBA.Int(200, 20, 20))
				}
		}
	}
}
