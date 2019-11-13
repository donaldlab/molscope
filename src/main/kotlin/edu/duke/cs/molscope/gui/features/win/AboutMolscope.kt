package edu.duke.cs.molscope.gui.features.win

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.Molscope
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.WindowFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class AboutMolscope : WindowFeature {

	override val id = FeatureId("about.molscope")

	var shouldOpen: Boolean = false

	private val popupId = id.toString()

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		if (menuItem("About ${Molscope.name}")) {
			shouldOpen = true
		}
	}

	override fun gui(imgui: Commands, win: WindowCommands) = imgui.run {

		if (shouldOpen) {
			shouldOpen = false
			openPopup(popupId)
		}

		popup(popupId) {
			text(Molscope.name)
			text("v${Molscope.version}")
			spacing()
			text("Developed by the Donald Lab")
			text("at Duke University")
		}
	}
}
