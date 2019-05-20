package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.vulkan.Queue
import cuchaz.kludge.vulkan.Semaphore
import cuchaz.kludge.vulkan.semaphore
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.render.SlideRenderer
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
		private set

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
			val imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose()
			this.rendererInfo = RendererInfo(renderer, imageDesc)

		} else {

			if (width == rendererInfo.renderer.width && height == rendererInfo.renderer.height) {

				// same size as before, don't resize
				return
			}

			// replace the old renderer
			val renderer = SlideRenderer(queue, width, height, rendererInfo.renderer).autoClose(replace = rendererInfo.renderer)
			val imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose(replace = rendererInfo.imageDesc)
			this.rendererInfo = RendererInfo(renderer, imageDesc)
		}
	}

	fun updateImageDesc() {
		rendererInfo?.let {
			it.imageDesc = Imgui.imageDescriptor(it.renderer.imageView, it.renderer.imageSampler)
				.autoClose(replace = it.imageDesc)
		}
	}

	fun render(slide: Slide.Locked, renderFinished: Semaphore): Boolean {
		val rendererInfo = rendererInfo ?: return false
		rendererInfo.renderer.render(slide, renderFinished)
		return true
	}


	// GUI state
	private val contentMin = Vector2f()
	private val contentMax = Vector2f()
	private val mousePos = Vector2f()
	private val dragDelta = Vector2f()
	private var dragStartAngle = 0f
	private var dragMode: DragMode = DragMode.RotateXY

	private fun Commands.getDragAngle(): Float {
		val renderer = rendererInfoOrThrow.renderer
		mousePos
			.apply { getMousePos(this) }
			.sub(Vector2f().apply { getItemRectMin(this) })
			.sub(renderer.extent.width.toFloat()/2, renderer.extent.height.toFloat()/2)
		return atan2(mousePos.y, mousePos.x)
	}

	fun gui(imgui: Commands) = imgui.apply {

		// start the window
		begin(slide.name)

		// track the window content area
		getWindowContentRegionMin(contentMin)
		getWindowContentRegionMax(contentMax)

		val rendererInfo = rendererInfo ?: run {
			end()
			return@apply
		}

		// draw the slide image
		setCursorPos(contentMin)
		image(rendererInfo.imageDesc)

		// draw a big invisible button over the image so we can capture mouse events
		setCursorPos(contentMin)
		invisibleButton("drag", rendererInfo.renderer.extent)
		if (isItemClicked(0)) {

			rendererInfo.cameraRotator.capture()

			// get the click pos relative to the image center, normalized by image size
			val extent = rendererInfo.renderer.extent
			mousePos
				.apply { getMousePos(this) }
				.sub(Vector2f().apply { getItemRectMin(this) })
				.sub(extent.width.toFloat()/2, extent.height.toFloat()/2)
				.mul(2f/extent.width, 2f/extent.height)
				.apply {
					x = abs(x)
					y = abs(y)
				}

			// pick the drag mode based on the click pos
			// if we're near the center, rotate about xy
			// otherwise, rotate about z
			val cutoff = 0.6
			dragMode = if (mousePos.x < cutoff && mousePos.y < cutoff) {
				DragMode.RotateXY
			} else {
				dragStartAngle = getDragAngle()
				DragMode.RotateZ
			}

		}
		if (isItemActive() && Imgui.io.mouse.buttonDown[0]) {

			// apply the drag rotations
			rendererInfo.cameraRotator.apply {
				q.identity()
				when (dragMode) {
					DragMode.RotateXY -> {
						getMouseDragDelta(0, dragDelta)
						q.rotateAxis(dragDelta.x/100f, up)
						q.rotateAxis(dragDelta.y/100f, side)
					}
					DragMode.RotateZ -> {
						q.rotateAxis(getDragAngle() - dragStartAngle, look)
					}
				}
				update()
			}
		}
		if (isItemHovered()) {

			// apply mouse wheel magnification
			val delta = Imgui.io.mouse.wheel
			if (delta != 0f) {
				rendererInfo.renderer.camera.magnification *= 1f + delta/10f
			}
		}

		end()
	}
}


private enum class DragMode {
	RotateXY,
	RotateZ
}
