package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.gui.features.FeatureId


abstract class WindowFeature(
	val menu: String,
	val name: String
) {

	val id = FeatureId(menu, name)

	/**
	 * Renders the menu item of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	open fun menu(imgui: Commands, win: WindowCommands) {
		// do nothing by default
	}

	/**
	 * Renders the GUI of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	open fun gui(imgui: Commands, win: WindowCommands) {
		// do nothing by default
	}
}

/**
 * Interface to talk to the window that is hosting this feature.
 *
 * Should only access this from the window thread.
 */
interface WindowCommands {
	var shouldClose: Boolean
}
