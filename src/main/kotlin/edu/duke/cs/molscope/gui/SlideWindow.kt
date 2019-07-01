package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.vulkan.Queue
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.render.*
import edu.duke.cs.molscope.render.OcclusionCalculator
import edu.duke.cs.molscope.render.SlideRenderer
import edu.duke.cs.molscope.render.SphereRenderable
import edu.duke.cs.molscope.render.ViewRenderables
import edu.duke.cs.molscope.tools.IdentityChangeTracker
import edu.duke.cs.molscope.view.*
import org.joml.Vector2f
import kotlin.NoSuchElementException
import kotlin.math.abs
import kotlin.math.atan2


internal class SlideWindow(
	val slide: Slide,
	val queue: Queue,
	val exceptionViewer: ExceptionViewer
): AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = also { closer.add(this@autoClose, replace) }
	override fun close() = closer.close()

	private inner class RendererInfo(
		val renderer: SlideRenderer,
		var imageDesc: Imgui.ImageDescriptor
	) {

		var cameraRotator = renderer.camera.Rotator()

		init {
			// if we already have an occlusion field, update the renderer right away
			occlusionField?.updateDescriptorSet(renderer)
		}
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

	private val renderablesTracker = RenderablesTracker()
	private val occlusionCalculator = OcclusionCalculator(queue).autoClose()
	private var occlusionField: OcclusionCalculator.Field? = null

	fun render(slide: Slide.Locked, renderFinished: Semaphore): Boolean {

		val rendererInfo = rendererInfo ?: return false

		rendererInfo.apply {

			// update the background color based on settings
			renderer.backgroundColor = backgroundColors[ColorsMode.current]!!

			// gather all the renderables by type
			// sadly we can't use an interface to collect SphereRenderable instances from views,
			// because these rendering details are internal, and the views are public API
			// alas, kotlin doesn't allow mixing internal interfaces into public classes
			// so, this is going to get a bit messy...
			val renderables = ViewRenderables(
				spheres = slide.views.mapNotNull { when (it) {
					is SpaceFilling -> it.sphereRenderable
					is BallAndStick -> it.sphereRenderable
					else -> null
				}},
				cylinders = slide.views.mapNotNull { when (it) {
					is BallAndStick -> it.cylinderRenderable
					else -> null
				}}
			)

			// did any renderables change?
			renderablesTracker.update(renderables)
			if (renderablesTracker.changed) {

				// update the occlusion field
				occlusionField = occlusionCalculator
					.Field(
						// TODO: make configurable?
						extent = Extent3D(16, 16, 16),
						gridSubdivisions = 2,
						renderables = renderables
					)
					.autoClose(replace = occlusionField)
					.apply {
						updateDescriptorSet(renderer)
					}
			}

			// get the occlusion field
			// if we don't have one, we must not have any geometry either, so skip the render
			val occlusionField = occlusionField ?: return false
			if (occlusionField.needsProcessing) {
				occlusionField.process()
			}

			renderer.render(slide, renderables, occlusionField, renderFinished)
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

	private val commands = object : SlideCommands {

		override fun showExceptions(block: () -> Unit) {
			try {
				block()
			} catch (t: Throwable) {
				exceptionViewer.add(t)
			}
		}

		override val renderSettings get() = rendererInfoOrThrow.renderer.settings

		override var hoverEffect: RenderEffect? = null
		override var mouseTarget: ViewIndexed? = null
		override var mouseLeftClick = false
	}

	fun gui(imgui: Commands) = imgui.run {

		// to start, the window title is the slide name
		var title = slide.name

		// append rendering progress to the window title
		occlusionField?.let { field ->
			val progress = field.processingProgress
			if (progress < 1.0) {
				title += " (lighting ${"%.0f".format(progress*100.0)}%)"
			}
		}

		// add a unique id for this window
		title += "###${System.identityHashCode(slide)}"

		// start the window
		setNextWindowSizeConstraints(
			320f, 240f,
			Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY
		)
		if (!begin(title, flags = IntFlags.of(Commands.BeginFlags.MenuBar, Commands.BeginFlags.NoBringToFrontOnFocus))) {
			end()
			return
		}

		// render the slide feature menus
		slide.lock { slide ->
			if (beginMenuBar()) {

				if (rendererInfo != null) {

					// render feature menus
					for (menu in slide.features.menus) {
						if (beginMenu(menu.name)) {
							for (feature in menu.features) {
								feature.menu(this, slide, commands)
							}
							endMenu()
						}
					}
				}

				endMenuBar()
			}
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

		// what's the mouse looking at?
		commands.mouseTarget =
			if (rendererInfo.renderer.cursorIndices.isEmpty) {
				null
			} else {
				slide.lock { slide ->
					slide.views.getOrNull(rendererInfo.renderer.cursorIndices.view)?.let { view ->
						view.getIndexed(rendererInfo.renderer.cursorIndices.target)?.let { target ->
							ViewIndexed(view, target)
						}
					}
				}
			}

		val isContextMenuOpen = isPopupOpen(ContextMenu.id)

		// translate ImGUI mouse inputs into events
		commands.mouseLeftClick = isItemClicked(0)
		if (commands.mouseLeftClick) {
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
				val isEnter = hoverPos == null
				val pos = hoverPos ?: Vector2f()
				getMouseOffset(pos)
				hoverPos = pos

				if (isEnter) {
					handleEnter(rendererInfo, pos)
				}
				handleHover(rendererInfo, pos)

				// handle mouse wheel
				val wheelDelta = Imgui.io.mouse.wheel
				if (wheelDelta != 0f) {
					handleWheel(rendererInfo, wheelDelta)
				}

			} else {

				// handle mouse position
				hoverPos?.let { oldPos ->
					hoverPos = null
					handleLeave(rendererInfo, oldPos)
				}
			}
		}
		if (beginPopupContextItem(ContextMenu.id)) {

			if (contextMenu == null) {
				val contextMenu = ContextMenu()

				// did we click on anything?
				commands.mouseTarget?.let { target ->

					// add slide features to the menu
					slide.lock { slide ->
						for (menu in slide.features.menus) {
							for (feature in menu.features) {
								feature.contextMenu(contextMenu, slide, commands, target)
							}
						}
					}
				}

				this@SlideWindow.contextMenu = contextMenu
			}

			// render the context menu if we have one
			contextMenu?.render(imgui)
			endPopup()
		}

		end()

		// render the slide feature windows
		slide.lock { slide ->
			for (menu in slide.features.menus) {
				for (feature in menu.features) {
					feature.gui(this, slide, commands)
				}
			}
		}
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
		val cutoff = 0.8
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

	private fun handleEnter(rendererInfo: RendererInfo, pos: Vector2f) {
		// nothing to do yet
	}

	private fun handleLeave(rendererInfo: RendererInfo, oldPos: Vector2f) {

		// turn off the cursor
		rendererInfo.renderer.cursorPos = null
	}

	private fun handleHover(rendererInfo: RendererInfo, pos: Vector2f) {

		// update the cursor
		rendererInfo.renderer.cursorPos = pos.toOffset()
		rendererInfo.renderer.cursorEffect = commands.hoverEffect
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


class ViewIndexed(val view: RenderView, val target: Any)


class ContextMenu {

	private val features = ArrayList<Commands.() -> Unit>()

	fun add(block: Commands.() -> Unit) {
		features.add(block)
	}

	fun render(imgui: Commands) = imgui.run {

		if (features.isEmpty()) {

			// no features, close the popup
			closeCurrentPopup()
		}

		for (feature in features) {
			feature()
		}
	}

	companion object {
		const val id = "contextMenu"
	}

	/* TODO: move into navigation/camera feature?
	fun foo() {
		text("Atom: ${atom.name}")
		text("\tat (%.3f,%.3f,%.3f)".format(atom.pos.x, atom.pos.y, atom.pos.y))

		// TEMP
		if (button("Center")) {
			closeCurrentPopup()
			// TODO: add translation animations
			renderer.camera.lookAt(atom.pos.toFloat())
		}
	}
	*/
}


internal class RenderablesTracker {

	private val spheres = IdentityChangeTracker<SphereRenderable>()
	private val cylinders = IdentityChangeTracker<CylinderRenderable>()

	var changed: Boolean = false
		private set

	fun update(renderables: ViewRenderables) {

		spheres.update(renderables.spheres)
		cylinders.update(renderables.cylinders)

		changed = spheres.changed || cylinders.changed
	}
}
