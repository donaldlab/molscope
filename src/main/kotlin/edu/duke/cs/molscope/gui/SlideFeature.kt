package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.vulkan.Extent2D
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.features.HasFeatureId
import edu.duke.cs.molscope.render.Camera
import edu.duke.cs.molscope.render.RenderEffect
import edu.duke.cs.molscope.render.RenderSettings
import org.joml.Vector2f
import org.joml.Vector2fc


interface SlideFeature : HasFeatureId {

	/**
	 * Renders the menu item of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) {
		// do nothing by default
	}

	/**
	 * Renders the GUI of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	fun gui(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) {
		// do nothing by default
	}

	/**
	 * Renders the context menu GUI of your feature.
	 *
	 * Called from the window thread, not the creating thread,
	 * so be careful about synchronization of shared memory.
	 */
	fun contextMenu(contextMenu: ContextMenu, slide: Slide.Locked, slidewin: SlideCommands, target: ViewIndexed) {
		// do nothing by default
	}
}

interface SlideCommands {

	fun showExceptions(block: () -> Unit)

	val extent: Extent2D
	val renderSettings: RenderSettings
	var hoverEffect: RenderEffect?

	val mouseTarget: ViewIndexed?
	val mouseLeftClick: Boolean
	val mouseLeftDrag: Boolean
	val mouseOffset: Vector2fc
	val mouseLeftDragDelta: Vector2fc
	val mouseWheelDelta: Float

	val camera: Camera
}
