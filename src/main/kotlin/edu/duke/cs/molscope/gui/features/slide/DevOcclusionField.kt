package edu.duke.cs.molscope.gui.features.slide

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.Ref
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.SlideCommands
import edu.duke.cs.molscope.gui.SlideFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class DevOcclusionField : SlideFeature {

	override val id = FeatureId("dev.occlusionfield")

	val pOn = Ref.of(false)

	override fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {
		pOn.value = slidewin.renderSettings.showOcclusionField
		if (menuItem("Show Occlusion Field", selected = pOn)) {
			slidewin.renderSettings.showOcclusionField = pOn.value
		}
	}
}
