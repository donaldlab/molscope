package edu.duke.cs.molscope.view

import org.joml.AABBf


interface RenderView {

	fun calcBoundingBox(): AABBf
}
