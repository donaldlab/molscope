package edu.duke.cs.molscope.gui.features.slide

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.SlideCommands
import edu.duke.cs.molscope.gui.SlideFeature
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.features.FeatureId


class CloseSlide(val win: WindowCommands) : SlideFeature {

	override val id = FeatureId("prep.close")

	override fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {
		if (menuItem("Close")) {
			win.removeSlide(slide.unlocked)
		}
	}
}
