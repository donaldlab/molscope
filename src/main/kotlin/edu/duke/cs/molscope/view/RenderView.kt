package edu.duke.cs.molscope.view

import edu.duke.cs.molscope.molecule.Molecule
import edu.duke.cs.molscope.molecule.MoleculeSelector
import edu.duke.cs.molscope.render.CylinderRenderable
import edu.duke.cs.molscope.render.MoleculeRenderEffects
import edu.duke.cs.molscope.render.SphereRenderable
import org.joml.AABBf


/**
 * Represents a renderable view of a Thing.
 */
interface RenderView {
	var isVisible: Boolean
	fun calcBoundingBox(): AABBf? = null
	fun getIndexed(index: Int): Any? = null
	val spheres: SphereRenderable? get() = null
	val cylinders: CylinderRenderable? get() = null
}


/**
 * Represents a renderable view of a Molecule.
 */
interface MoleculeRenderView : RenderView {
	val mol: Molecule
	var selector: MoleculeSelector
	fun moleculeChanged()
	val renderEffects: MoleculeRenderEffects
}
