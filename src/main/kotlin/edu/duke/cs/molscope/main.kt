package edu.duke.cs.molscope

import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.render.SlideRenderer
import edu.duke.cs.molscope.render.WindowRenderer


fun main() = autoCloser {

	// run the window renderer
	val win =
		WindowRenderer(
			width = 800,
			height = 600,
			title = "MolScope"
		)
		.autoClose()

	// GUI state
	//val winOpen = Ref.of(true)
	//val check = Ref.of(false)
	//var counter = 0

	// TODO: make the slide API cleaner, hide rendering details

	// prepare a slide
	val slide = SlideRenderer(
		win.device,
		win.graphicsFamily,
		320,
		240
	).autoClose()
	val slideImageDesc = Imgui.imageDescriptor(slide.imageView, slide.imageSampler).autoClose()

	win.renderLoop(
		blockRender = {

			// TODO: need anything here?
		},
		blockGui = {

			// TEMP: debug window
			setNextWindowSize(400f, 200f)
			begin("Rendering info")
			text("display size: ${Imgui.io.displaySize.width} x ${Imgui.io.displaySize.height}")
			text("frame time: ${String.format("%.3f", 1000f*Imgui.io.deltaTime)} ms")
			text("FPS: ${String.format("%.3f", Imgui.io.frameRate)}")
			end()

			slide.render()
			begin("Slide")
			image(slideImageDesc)
			end()
		}
	)

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
