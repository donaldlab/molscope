package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.toFloat
import cuchaz.kludge.tools.x
import cuchaz.kludge.tools.y
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.molecule.Atom
import edu.duke.cs.molscope.render.SlideRenderer
import edu.duke.cs.molscope.view.ColorsMode
import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.atan2


internal class SlideWindow(
	val slide: Slide,
	val queue: Queue
): AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = also { closer.add(this@autoClose, replace) }
	override fun close() = closer.close()

	private class RendererInfo(
		val renderer: SlideRenderer,
		var imageDesc: Imgui.ImageDescriptor
	) {

		var cameraRotator = renderer.camera.Rotator()
	}

	private var rendererInfo: RendererInfo? = null

	private val rendererInfoOrThrow: RendererInfo get() = rendererInfo ?: throw NoSuchElementException("no renderer yet, this is a bug")

	val renderFinished = queue.device.semaphore().autoClose()

	fun resizeIfNeeded() {

		// how big is the window content area?
		val width = (contentMax.x - contentMin.x).toInt()
		val height = (contentMax.y - contentMin.y).toInt()

		if (width <= 0 || height <= 0) {

			// not big enough, don't bother resizing
			return
		}

		val rendererInfo = this.rendererInfo
		if (rendererInfo == null) {

			// make a new renderer
			val renderer = SlideRenderer(queue, width, height).autoClose()
			val imageDesc = Imgui.imageDescriptor(renderer.postView, renderer.sampler).autoClose()
			this.rendererInfo = RendererInfo(renderer, imageDesc)

		} else {

			if (width == rendererInfo.renderer.width && height == rendererInfo.renderer.height) {

				// same size as before, don't resize
				return
			}

			// replace the old renderer
			val renderer = SlideRenderer(queue, width, height, rendererInfo.renderer).autoClose(replace = rendererInfo.renderer)
			val imageDesc = Imgui.imageDescriptor(renderer.postView, renderer.sampler).autoClose(replace = rendererInfo.imageDesc)
			this.rendererInfo = RendererInfo(renderer, imageDesc)
		}
	}

	fun updateImageDesc() {
		rendererInfo?.let {
			it.imageDesc = Imgui.imageDescriptor(it.renderer.postView, it.renderer.sampler)
				.autoClose(replace = it.imageDesc)
		}
	}

	// TODO: allow configurable hover/selection mode?
	var showHovers: Boolean = true

	fun render(slide: Slide.Locked, renderFinished: Semaphore): Boolean {
		val rendererInfo = rendererInfo ?: return false
		rendererInfo.renderer.apply {

			// update the background color based on settings
			backgroundColor = backgroundColors[ColorsMode.current]!!
			
			render(slide, renderFinished)
		}
		return true
	}


	// GUI state
	private val contentMin = Vector2f()
	private val contentMax = Vector2f()
	private var hoverPos: Vector2f? = null
	private var dragStartAngle = 0f
	private var dragMode: DragMode = DragMode.RotateXY
	private var contextMenu: ContextMenu? = null

	private fun Commands.getMouseOffset(out: Vector2f = Vector2f()) =
		out
			.apply { getMousePos(this) }
			.sub(Vector2f().apply { getItemRectMin(this) })

	private fun Commands.getDragDelta(button: Int) =
		Vector2f().apply { getMouseDragDelta(button, this) }

	private fun getDragAngle(mousePos: Vector2f): Float {
		val renderer = rendererInfoOrThrow.renderer
		return atan2(
			mousePos.y - renderer.extent.height.toFloat()/2,
			mousePos.x - renderer.extent.width.toFloat()/2
		)
	}

	fun gui(imgui: Commands) = imgui.run {

		// start the window
		if (!begin(slide.name)) {
			end()
			return
		}

		// track the window content area
		getWindowContentRegionMin(contentMin)
		getWindowContentRegionMax(contentMax)

		val rendererInfo = rendererInfo ?: run {
			end()
			return
		}

		// draw the slide image
		setCursorPos(contentMin)
		image(rendererInfo.imageDesc)

		// draw a big invisible button over the image so we can capture mouse events
		setCursorPos(contentMin)
		invisibleButton("button", rendererInfo.renderer.extent)

		val isContextMenuOpen = isPopupOpen(ContextMenu.id)

		// translate ImGUI mouse inputs into events
		if (isItemClicked(0)) {
			handleLeftDown(rendererInfo, getMouseOffset())
		}
		if (isItemActive() && Imgui.io.mouse.buttonDown[0]) {
			handleLeftDrag(rendererInfo, getMouseOffset(), getDragDelta(0))
		}

		// handle context menus
		if (!isContextMenuOpen) {
			contextMenu = null

			// update hover effects only when the context menu isn't open
			if (isItemHovered()) {

				// handle mouse position
				var hoverPos = this@SlideWindow.hoverPos
				if (hoverPos == null) {
					hoverPos = getMouseOffset()
					handleEnter(rendererInfo)
				} else {
					getMouseOffset(hoverPos)
				}
				this@SlideWindow.hoverPos = hoverPos
				handleHover(rendererInfo)

				// handle mouse wheel
				val wheelDelta = Imgui.io.mouse.wheel
				if (wheelDelta != 0f) {
					handleWheel(rendererInfo, wheelDelta)
				}

			} else {

				// handle mouse position
				if (hoverPos != null) {
					hoverPos = null
					handleLeave(rendererInfo)
				}
			}
		}
		if (beginPopupContextItem(ContextMenu.id)) {

			if (contextMenu == null) {
				contextMenu = makeContextMenu(rendererInfo)
			}

			// render the context menu if we have one
			contextMenu?.render(rendererInfo.renderer, imgui)
			endPopup()
		}

		end()
	}

	private fun handleLeftDown(rendererInfo: RendererInfo, mousePos: Vector2f) {

		// start a new camera rotation
		rendererInfo.cameraRotator.capture()

		// get the normalized click dist from center
		val w = rendererInfo.renderer.extent.width.toFloat()
		val h = rendererInfo.renderer.extent.height.toFloat()
		val dx = abs(mousePos.x*2f/w - 1f)
		val dy = abs(mousePos.y*2f/h - 1f)

		// pick the drag mode based on the click pos
		// if we're near the center, rotate about xy
		// otherwise, rotate about z
		val cutoff = 0.6
		dragMode = if (dx < cutoff && dy < cutoff) {
			DragMode.RotateXY
		} else {
			dragStartAngle = getDragAngle(mousePos)
			DragMode.RotateZ
		}
	}

	private fun handleLeftDrag(rendererInfo: RendererInfo, mousePos: Vector2f, delta: Vector2f) {

		// apply the drag rotations
		rendererInfo.cameraRotator.apply {
			q.identity()
			when (dragMode) {
				DragMode.RotateXY -> {
					q.rotateAxis(delta.x/100f, up)
					q.rotateAxis(delta.y/100f, side)
				}
				DragMode.RotateZ -> {
					q.rotateAxis(getDragAngle(mousePos) - dragStartAngle, look)
				}
			}
			update()
		}
	}

	private fun makeContextMenu(rendererInfo: RendererInfo): ContextMenu {

		// did we click on something?
		rendererInfo.renderer.cursorIndex?.let { cursorIndex ->

			slide.lock { slide ->

				// yup, what did we click on?
				val target = slide.views[cursorIndex.viewIndex].getIndexed(cursorIndex.index)

				// make a new context menu based on the target
				return when (target) {
					null -> ContextMenu.Close()
					is Atom -> ContextMenu.AtomMenu(target)
					else -> ContextMenu.StringMenu(target.toString())
				}
			}
		}

		// nope, kill the popup
		return ContextMenu.Close()
	}

	private fun handleEnter(rendererInfo: RendererInfo) {
		// nothing to do yet
	}

	private fun handleLeave(rendererInfo: RendererInfo) {

		// turn off the hover effects if needed
		rendererInfo.renderer.cursorPos = null
	}

	private fun handleHover(rendererInfo: RendererInfo) {

		// pass the hover pos to the renderer to turn on hover effects, if needed
		if (showHovers) {

			hoverPos?.let {
				rendererInfo.renderer.cursorPos = it.toOffset()
			}
		}
	}

	private fun handleWheel(rendererInfo: RendererInfo, wheelDelta: Float) {

		// apply mouse wheel magnification
		rendererInfo.renderer.camera.magnification *= 1f + wheelDelta/10f
	}
}


private val backgroundColors = mapOf(
	ColorsMode.Dark to ColorRGBA.Int(0, 0, 0),
	ColorsMode.Light to ColorRGBA.Int(255, 255, 255)
)

private enum class DragMode {
	RotateXY,
	RotateZ
}


private sealed class ContextMenu {

	abstract fun render(renderer: SlideRenderer, imgui: Commands)

	companion object {
		const val id = "contextMenu"
	}

	class Close: ContextMenu() {
		override fun render(renderer: SlideRenderer, imgui: Commands) = imgui.run {
			closeCurrentPopup()
		}
	}

	class AtomMenu(val atom: Atom): ContextMenu() {
		override fun render(renderer: SlideRenderer, imgui: Commands) = imgui.run {

			text("Atom: ${atom.name}")
			text("\tat (%.3f,%.3f,%.3f)".format(atom.pos.x, atom.pos.y, atom.pos.y))

			if (button("Center")) {
				closeCurrentPopup()
				// TODO: add translation animations
				renderer.camera.lookAt(atom.pos.toFloat())
			}
		}
	}

	class StringMenu(val msg: String): ContextMenu() {
		override fun render(renderer: SlideRenderer, imgui: Commands) = imgui.run {
			text(msg)
		}
	}
}
