package edu.duke.cs.molscope.gui.features.slide

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.Extent2D
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.*
import edu.duke.cs.molscope.gui.features.FeatureId
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.molecule.Polymer
import edu.duke.cs.molscope.render.Camera
import edu.duke.cs.molscope.render.RenderEffect
import edu.duke.cs.molscope.view.MoleculeRenderView
import org.joml.Vector2fc
import org.joml.Vector3fc
import kotlin.math.abs
import kotlin.math.atan2


class NavigationTool : SlideFeature {

	override val id = FeatureId("navigation")

	private val pOpen = Ref.of(false)
	private var wasOpen = false

	private fun Slide.Locked.molViews() = views.mapNotNull { it as? MoleculeRenderView }

	private fun List<MoleculeRenderView>.clearSelections() = forEach { it.renderEffects.clear() }

	override fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {
		if (menuItem("Navigate")) {
			pOpen.value = true
		}
	}

	override fun gui(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {

		val molViews = slide.molViews()

		// handle mouse interactions, even if the window isn't open
		if (slidewin.mouseLeftClick) {
			click(slidewin)
		}
		if (slidewin.mouseLeftDrag) {
			drag(slidewin)
		}
		if (slidewin.mouseWheelDelta != 0f) {
			wheel(slidewin)
		}

		if (pOpen.value) {
			if (!wasOpen) {

				// add the hover effect
				slidewin.hoverEffects[id] = hoverEffect
			}
			wasOpen = true

			// draw the window
			begin("Navigator##${slide.name}", pOpen, IntFlags.of(Commands.BeginFlags.AlwaysAutoResize))

			// show camera properties
			sliderFloat("Magnification", Ref.of(slidewin.camera::magnification), 1f, 200f, "%.1fx", power=4f)
			sliderFloat("View Distance", Ref.of(slidewin.camera::viewDistance), 1f, 400f, "%.1f", power=4f)

			end()

		} else if (wasOpen) {
			wasOpen = false

			// remove the hover effect
			slidewin.hoverEffects.remove(id)

			// clear any leftover selections when the window closes
			molViews.clearSelections()
		}
	}

	override fun contextMenu(contextMenu: ContextMenu, slide: Slide.Locked, slidewin: SlideCommands, target: ViewIndexed) {

		if (!pOpen.value) {
			return
		}

		// get the atom, if any
		val view = target.view as? MoleculeRenderView ?: return
		val mol = view.mol
		val atom = target.target as? Atom ?: return

		contextMenu.add {

			// show details about the atom
			text("Atom: ${atom.name} ${atom.element}")
			indent(10f)

			// show the position
			text("pos: ")
			sameLine()
			val coordsText = Commands.TextBuffer.of("%.3f,%.3f,%.3f".format(atom.pos.x, atom.pos.y, atom.pos.z()))
			inputText("", coordsText, IntFlags.of(Commands.InputTextFlags.ReadOnly))

			// show the molecule
			text("mol: ${mol.name}")

			// show the residue, if any
			if (mol is Polymer) {
				mol.chains
					.mapNotNull { chain ->
						chain.residues.find { atom in it.atoms }
					}
					.firstOrNull()
					?.let { res ->
						text("res: ${res.id} ${res.type}")
					}
			}

			unindent(10f)

			// show a button to center the camera on the atom
			if (button("Center")) {
				closeCurrentPopup()
				centerOn(slidewin, atom.pos.toFloat())
			}
		}
	}

	private var cameraRotator: Camera.Rotator? = null
	private var dragStartAngle = 0f
	private var dragMode: DragMode = DragMode.RotateXY

	private enum class DragMode {
		RotateXY,
		RotateZ
	}

	private fun getDragAngle(mouseOffset: Vector2fc, extent: Extent2D): Float {
		return atan2(
			mouseOffset.y - extent.height.toFloat()/2,
			mouseOffset.x - extent.width.toFloat()/2
		)
	}

	private fun click(slidewin: SlideCommands) {

		// reset the camera rotator
		val cameraRotator = cameraRotator ?: slidewin.camera.Rotator().apply { cameraRotator = this }
		cameraRotator.capture()

		// get the normalized click dist from center
		val w = slidewin.extent.width.toFloat()
		val h = slidewin.extent.height.toFloat()
		val dx = abs(slidewin.mouseOffset.x*2f/w - 1f)
		val dy = abs(slidewin.mouseOffset.y*2f/h - 1f)

		// pick the drag mode based on the click pos
		// if we're near the center, rotate about xy
		// otherwise, rotate about z
		val cutoff = 0.8
		dragMode = if (dx < cutoff && dy < cutoff) {
			DragMode.RotateXY
		} else {
			dragStartAngle = getDragAngle(slidewin.mouseOffset, slidewin.extent)
			DragMode.RotateZ
		}
	}

	private fun drag(slidewin: SlideCommands) {

		// apply the drag rotations
		cameraRotator?.apply {
			q.identity()
			when (dragMode) {
				DragMode.RotateXY -> {
					q.rotateAxis(slidewin.mouseLeftDragDelta.x/100f, up)
					q.rotateAxis(slidewin.mouseLeftDragDelta.y/100f, side)
				}
				DragMode.RotateZ -> {
					q.rotateAxis(getDragAngle(slidewin.mouseOffset, slidewin.extent) - dragStartAngle, look)
				}
			}
			update()
		}
	}

	private fun wheel(slidewin: SlideCommands) {

		// adjust the magnification
		slidewin.camera.magnification *= 1f + slidewin.mouseWheelDelta/10f
	}

	private fun centerOn(slidewin: SlideCommands, pos: Vector3fc) {

		// move the camera to the target position
		slidewin.camera.lookAt(pos)
	}
}

private val hoverEffect = RenderEffect(
	ByteFlags.of(RenderEffect.Flags.Highlight, RenderEffect.Flags.Inset, RenderEffect.Flags.Outset),
	200u, 200u, 200u
)