package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.features.FeatureId
import edu.duke.cs.molscope.gui.features.HasFeatureId
import edu.duke.cs.molscope.render.RenderEffect
import edu.duke.cs.molscope.render.RenderSettings


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

	/**
	 * Renders the context menu GUI of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	open fun contextMenu(contextMenu: ContextMenu, slide: Slide.Locked, slidewin: SlideCommands, target: ViewIndexed) {
		// do nothing by default
	}
}

interface SlideCommands {
	val renderSettings: RenderSettings
	var hoverEffect: RenderEffect?
	val mouseTarget: ViewIndexed?
	val mouseLeftClick: Boolean
}
