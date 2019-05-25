package edu.duke.cs.molscope.gui.features.win

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.WindowFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class FileExit : WindowFeature(menu, name) {

	companion object {
		const val menu = "File"
		const val name = "Exit"
		val id = FeatureId(menu, name)
	}

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		if (menuItem("Exit")) {
			win.shouldClose = true
		}
	}
}
