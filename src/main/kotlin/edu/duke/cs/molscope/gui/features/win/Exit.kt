package edu.duke.cs.molscope.gui.features.win

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.WindowFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class Exit : WindowFeature {

	override val id = FeatureId("exit")

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		if (menuItem("Exit")) {
			win.shouldClose = true
		}
	}
}
