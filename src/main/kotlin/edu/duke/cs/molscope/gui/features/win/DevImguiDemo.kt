package edu.duke.cs.molscope.gui.features.win

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.Ref
import edu.duke.cs.molscope.gui.WindowCommands
import edu.duke.cs.molscope.gui.WindowFeature
import edu.duke.cs.molscope.gui.features.FeatureId


class DevImguiDemo : WindowFeature(menu, name) {

	companion object {
		const val menu = "Dev"
		const val name = "ImGUI Demo"
		val id = FeatureId(menu, name)
	}

	val pOpen = Ref.of(false)

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		if (menuItem(name)) {
			pOpen.value = true
		}
	}

	override fun gui(imgui: Commands, win: WindowCommands) = imgui.run {
		if (pOpen.value) {
			showDemoWindow(pOpen)
		}
	}
}