package edu.duke.cs.molscope

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.imgui.context
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.window.Monitors
import cuchaz.kludge.window.Size
import cuchaz.kludge.window.Window as KWindow
import cuchaz.kludge.window.Windows
import edu.duke.cs.molscope.render.Camera
import edu.duke.cs.molscope.render.VulkanDevice
import edu.duke.cs.molscope.render.SlideRenderer
import edu.duke.cs.molscope.render.WindowRenderer
import org.joml.Vector2f
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.atan2


class Window(
	title: String = "MolScope",
	width: Int = 800,
	height: Int = 600
) : AutoCloseable {

	// start the window and renderer on a dedicated thread
	private lateinit var windowThread: WindowThread
	private val latch = CountDownLatch(1)
	private val thread =
		Thread {
			WindowThread(title, Size(width, height)).use {
				windowThread = it
				latch.countDown()
				windowThread.renderLoop()
			}
		}
		.apply {
			name = "Window"
			isDaemon = false
			start()
		}

	init {
		// wait for the thread to start
		latch.await()
	}

	override fun close() {
		if (thread.isAlive) {
			sync {
				// ask the thread to stop by asking the window to close
				win.shouldClose = true
			}
			thread.join(1000)
		}
	}

	fun waitForClose() {
		thread.join()
	}

	/**
	 * when on the caller thread, never access windowThread directly
	 * always call sync() to access the windowThread at the right time
	 */
	private fun <R> sync(block: WindowThread.() -> R): R = synchronized(windowThread) { windowThread.block() }

	var backgroundColor: ColorRGBA
		get() = sync { renderer.backgroundColor }
		set(value) { sync { renderer.backgroundColor = value } }


	inner class Slides {

		private val slides: ArrayList<Slide> = ArrayList()

		fun add(slide: Slide) {

			slides.add(slide)

			// also update the renderer
			sync {
				addSlide(slide)
			}
		}

		fun remove(slide: Slide) {

			slides.remove(slide)

			sync {
				removeSlide(slide)
			}
		}
	}
	val slides = Slides()
}


internal class WindowThread(
	title: String,
	size: Size
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = also { closer.add(this@autoClose, replace) }
	private fun <R> R.autoClose(block: R.() -> Unit) = also { closer.add { block() } }
	override fun close() = closer.close()

	init {

		// TODO: only call this once for all windows?

		// init the window manager
		Windows.init()
		Windows.autoClose()

		// check for vulkan support from the window manager
		if (!Windows.isVulkanSupported) {
			throw Error("No Vulkan support from window manager")
		}

		// listen to problems from the window manager
		Windows.errors.setOut(System.err)
	}

	// make a window and show it
	val win = KWindow(title, size)
		.autoClose()
		.apply {
			centerOn(Monitors.findBest(size))
			visible = true
		}

	val vk = VulkanDevice(
		vulkanExtensions = Windows.requiredVulkanExtensions
	).autoClose()

	// make a surface for the window
	val surface = vk.vulkan.surface(win).autoClose()

	// create the device and the queues
	val graphicsFamily = vk.physicalDevice.findQueueFamily(IntFlags.of(PhysicalDevice.QueueFamily.Flags.Graphics))
	val surfaceFamily = vk.physicalDevice.findQueueFamily(surface)
	val device = vk.physicalDevice.device(
		queuePriorities = mapOf(
			graphicsFamily to listOf(1.0f),
			surfaceFamily to listOf(1.0f)
		),
		features = vk.deviceFeatures,
		extensionNames = setOf(PhysicalDevice.SwapchainExtension)
	).autoClose()
	val graphicsQueue = device.queues[graphicsFamily]!![0]
	val surfaceQueue = device.queues[surfaceFamily]!![0]

	init {
		// init ImGUI
		Imgui.load().autoClose()
		Imgui.context().autoClose()

		// configure ImGUI
		Imgui.io.configWindowsMoveFromTitleBarOnly = true
	}

	var renderer = WindowRenderer(win, vk, device, graphicsQueue, surfaceQueue, surface).autoClose()

	fun renderLoop() {

		while (!win.shouldClose) {
			synchronized(this) {

				Windows.pollEvents()

				// render the slides
				for (info in slides.values) {
					info.renderer.render(info.semaphore)
				}

				// render the window
				try {
					renderer.render(slides.values.map { it.semaphore }) {

						// TEMP: demo window
						//showDemoWindow()

						// draw the slides on the window
						for (info in slides.values) {
							info.gui(this)
						}

						// TEMP: debug window
						setNextWindowSize(400f, 200f)
						begin("Rendering info")
						text("display size: ${Imgui.io.displaySize.width} x ${Imgui.io.displaySize.height}")
						text("frame time: ${String.format("%.3f", 1000f*Imgui.io.deltaTime)} ms")
						text("FPS: ${String.format("%.3f", Imgui.io.frameRate)}")
						end()
					}
				} catch (ex: SwapchainOutOfDateException) {

					// re-create the renderer
					renderer.waitForIdle()
					renderer = WindowRenderer(win, vk, device, graphicsQueue, surfaceQueue, surface, renderer.swapchain)
						.autoClose(replace = renderer)

					// update the image descriptors for the slides
					for (info in slides.values) {
						info.updateImageDesc()
					}
				}
			}
		}

		// wait for the device to finish before starting cleanup
		renderer.waitForIdle()
	}

	private enum class DragMode {
		RotateXY,
		RotateZ
	}

	// TODO: this class is getting bigger, move it into a separate file?
	inner class SlideInfo(val slide: Slide): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = also { closer.add(this@autoClose, replace) }
		override fun close() = closer.close()

		val renderer = SlideRenderer(
			device,
			graphicsQueue,
			320, // TODO: allow resizing slides
			240
		).autoClose()

		val semaphore = device.semaphore().autoClose()

		private var imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose()

		fun updateImageDesc() {
			imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose(replace = imageDesc)
		}

		// GUI state
		private val contentMin = Vector2f()
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
			setNextWindowContentSize(renderer.extent)
			begin(slide.name)

			// get the window content area
			getWindowContentRegionMin(contentMin)

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

	val slides = HashMap<Slide,SlideInfo>()
		.autoClose {
			// cleanup any leftover slides
			for (info in values) {
				info.close()
			}
			clear()
		}
	val slideSemaphores = ArrayList<Semaphore>()

	fun addSlide(slide: Slide) {
		if (!slides.containsKey(slide)) {
			slides[slide] = SlideInfo(slide)
		}
	}

	fun removeSlide(slide: Slide) {
		val info = slides.remove(slide) ?: return
		info.close()
	}
}
