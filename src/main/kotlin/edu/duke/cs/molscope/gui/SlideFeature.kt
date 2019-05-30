package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.features.FeatureId
import edu.duke.cs.molscope.gui.features.HasFeatureId
import edu.duke.cs.molscope.render.RenderSettings
import edu.duke.cs.molscope.render.SlideRenderer


abstract class SlideFeature(
	val menu: String,
	val name: String
) : HasFeatureId {

	override val id = FeatureId(menu, name)

	/**
	 * Renders the menu item of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	open fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) {
		// do nothing by default
	}

	/**
	 * Renders the GUI of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	open fun gui(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) {
		// do nothing by default
	}
}

interface SlideCommands {
	val renderSettings: RenderSettings
}
