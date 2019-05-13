package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.vulkan.Queue
import cuchaz.kludge.vulkan.semaphore
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.render.Camera
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

	var renderer = SlideRenderer(
		queue,
		320, // pick an arbitrary initial size (doesn't matter what, we'll get resized later)
		240
	).autoClose()

	val semaphore = queue.device.semaphore().autoClose()

	private var imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose()

	fun updateImageDesc() {
		imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose(replace = imageDesc)
	}

	fun resizeIfNeeded() {

		// how big is the window content area?
		val width = (contentMax.x - contentMin.x).toInt()
		val height = (contentMax.y - contentMin.y).toInt()

		if (width <= 0 || height <= 0) {

			// not big enough, don't bother resizing
			return
		}

		if (width == renderer.width && height == renderer.height) {

			// same size as before, don't resize
			return
		}

		renderer = SlideRenderer(queue, width, height, renderer).autoClose(replace = renderer)
		updateImageDesc()

		// get a new camera rotator
		cameraRotator = renderer.camera.Rotator()
	}

	// GUI state
	private val contentMin = Vector2f()
	private val contentMax = Vector2f()
	private val mousePos = Vector2f()
	private val dragDelta = Vector2f()
	private var dragStartAngle = 0f
	private var cameraRotator: Camera.Rotator = renderer.camera.Rotator()
	private var dragMode: DragMode = DragMode.RotateXY

	private fun Commands.getDragAngle(): Float {
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

		// draw the slide image
		setCursorPos(contentMin)
		image(imageDesc)

		// draw a big invisible button over the image so we can capture mouse events
		setCursorPos(contentMin)
		invisibleButton("drag", renderer.extent)
		if (isItemClicked(0)) {

			cameraRotator.capture()

			// get the click pos relative to the image center, normalized by image size
			mousePos
				.apply { getMousePos(this) }
				.sub(Vector2f().apply { getItemRectMin(this) })
				.sub(renderer.extent.width.toFloat()/2, renderer.extent.height.toFloat()/2)
				.mul(2f/renderer.extent.width, 2f/renderer.extent.height)
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
		if (isItemActive()) {
			if (Imgui.io.mouse.buttonDown[0]) {

				// apply the drag rotations
				cameraRotator.apply {
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
		}

		end()
	}
}


private enum class DragMode {
	RotateXY,
	RotateZ
}
