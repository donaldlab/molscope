package edu.duke.cs.molscope

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*


fun main() = autoCloser {

	// open a window
	val win = Window(
		width = 800,
		height = 600,
		title = "MolScope"
	).autoClose()

	// prepare a slide
	// TODO: put something on the slide
	val slide = Slide("Slide")
	win.slides.add(slide)

	win.waitForClose()

} // end of scope here cleans up all autoClose() resources


enum class Element(val symbol: String, val radius: Float, val color: ColorRGBA) {

	Hydrogen("H", 1f, ColorRGBA.Int(200, 200, 200)),
	Carbon("C", 1.75f, ColorRGBA.Int(60, 60, 60)),
	Nitrogen("N", 1.55f, ColorRGBA.Int(20, 20, 200)),
	Oxygen("O", 1.4f, ColorRGBA.Int(200, 20, 20));

	companion object {

		operator fun get(symbol: String) =
			values()
				.find { it.symbol == symbol }
				?: throw NoSuchElementException("unknown element: $symbol")
				// haha, the class name literally means element this time!
	}
}
