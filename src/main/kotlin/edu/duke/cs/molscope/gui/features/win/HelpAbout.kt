package edu.duke.cs.molscope.gui.features.win

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.Molscope
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.WindowFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class HelpAbout : WindowFeature(menu, name) {

	companion object {
		const val menu = "Help"
		const val name = "About"
		val id = FeatureId(menu, name)
	}

	var shouldOpen: Boolean = false

	private val popupId = id.toString()

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		if (menuItem(name)) {
			shouldOpen = true
		}
	}

	override fun gui(imgui: Commands, win: WindowCommands) = imgui.run {

		if (shouldOpen) {
			shouldOpen = false
			openPopup(popupId)
		}

		if (beginPopup(popupId)) {
			text(Molscope.name)
			text("v${Molscope.version}")
			spacing()
			text("Developed by the Donald Lab")
			text("at Duke University")
			endPopup()
		}
	}
}
