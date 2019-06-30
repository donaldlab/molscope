package edu.duke.cs.molscope.view

import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.render.MoleculeRenderEffects
import org.joml.AABBf


/**
 * Represents a renderable view of a Thing.
 */
interface RenderView {
	fun calcBoundingBox(): AABBf
	fun getIndexed(index: Int): Any? = null
}


/**
 * Represents a renderable view of a Molecule.
 */
interface MoleculeRenderView : RenderView {
	val mol: Molecule
	fun moleculeChanged()
	val renderEffects: MoleculeRenderEffects
}
