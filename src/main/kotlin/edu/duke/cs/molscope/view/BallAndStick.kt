package edu.duke.cs.molscope.view

import cuchaz.kludge.tools.expandToInclude
import cuchaz.kludge.tools.skip
import cuchaz.kludge.vulkan.ColorRGBA
import cuchaz.kludge.vulkan.putColor4Bytes
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Element
import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.render.CylinderRenderable
import edu.duke.cs.molscope.render.CylinderRenderer
import edu.duke.cs.molscope.render.SphereRenderable
import edu.duke.cs.molscope.render.SphereRenderer
import org.joml.AABBf
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * create a view of the molecule at this point in time
 */
class BallAndStick(
	mol: Molecule
	// TODO: molecule subset selection?
): RenderView {

	// render the atoms as spheres
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
						putFloat(atomRadius)
						putColor4Bytes(color)
					}

					// TODO: allow different indexing strategies (eg residue, molecule)
					putInt(atomIndex)
				}
				flip()
			}
	}

	// render the bonds as cylinders
	internal val cylinderRenderable = object : CylinderRenderable {

		override val numIndices = mol.bonds.size*2

		override val vertexBuf =
			ByteBuffer.allocate(CylinderRenderer.vertexInput.size.toInt()*mol.atoms.size).apply {

				// use native byte ordering so we can efficiently copy to the GPU
				order(ByteOrder.nativeOrder())

				mol.atoms.forEachIndexed { atomIndex, atom ->

					// downgrade atom pos to floats for rendering
					putFloat(atom.pos.x().toFloat())
					putFloat(atom.pos.y().toFloat())
					putFloat(atom.pos.z().toFloat())

					putFloat(bondRadius)
					putColor4Bytes(ElementProps[atom].color)

					// TODO: allow different indexing strategies (eg residue, molecule)
					putInt(atomIndex)
				}
				flip()
			}

		override val indexBuf =
			ByteBuffer.allocate(Int.SIZE_BYTES*numIndices).apply {

				// use native byte ordering so we can efficiently copy to the GPU
				order(ByteOrder.nativeOrder())

				for (bond in mol.bonds) {
					putInt(bond.i1)
					putInt(bond.i2)
				}
				flip()
			}
	}

	override fun calcBoundingBox() =
		AABBf().apply {

			val r = atomRadius

			sphereRenderable.vertexBuf.rewind()
			for (i in 0 until sphereRenderable.numSpheres) {

				val x = sphereRenderable.vertexBuf.float
				val y = sphereRenderable.vertexBuf.float
				val z = sphereRenderable.vertexBuf.float
				sphereRenderable.vertexBuf.skip(12)

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

	companion object {

		// TODO: make these parameters
		private const val atomRadius = 0.2f
		private const val bondRadius = 0.2f
	}

	private data class ElementProps(
		val color: ColorRGBA.Int
	) {

		companion object {

			operator fun get(atom: Atom) =
				when (atom.element) {
					Element.Hydrogen -> ElementProps(ColorRGBA.Int(200, 200, 200))
					Element.Carbon -> ElementProps(ColorRGBA.Int(60, 60, 60))
					Element.Nitrogen -> ElementProps(ColorRGBA.Int(20, 20, 200))
					Element.Oxygen -> ElementProps(ColorRGBA.Int(200, 20, 20))
				}
		}
	}
}
