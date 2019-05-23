package edu.duke.cs.molscope.view

import org.joml.AABBf


/**
 * Represents a static view of a Thing (eg a molecule) at a point in time.
 *
 * Once constructed, the view is independent of the Thing.
 * If the Thing changes, the view should remain the same.
 */
interface RenderView {

	fun calcBoundingBox(): AABBf
	fun getIndexed(index: Int): Any? = null
}
