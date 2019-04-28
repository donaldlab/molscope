package edu.duke.cs.molscope

import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.Ref
import cuchaz.kludge.vulkan.ColorRGBA
import cuchaz.kludge.vulkan.Semaphore
import cuchaz.kludge.vulkan.semaphore
import cuchaz.kludge.window.Monitors
import cuchaz.kludge.window.Size
import cuchaz.kludge.window.Window as KWindow
import cuchaz.kludge.window.Windows
import edu.duke.cs.molscope.render.SlideRenderer
import edu.duke.cs.molscope.render.WindowRenderer
import java.util.concurrent.CountDownLatch


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
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
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
	// TODO: allow resizing the window
	val win = KWindow(title, size)
		.autoClose()
		.apply {
			centerOn(Monitors.primary)
			visible = true
		}

	val renderer = WindowRenderer(win).autoClose()

	val slidesSemaphore = renderer.device.semaphore().autoClose()

	fun renderLoop() {

		while (!renderer.win.shouldClose) {
			synchronized(this) {

				// render the slides
				for (info in slides.values) {
					info.renderer.render(info.semaphore)
				}

				// render the window
				renderer.render(slides.values.map { it.semaphore }) {

					// TEMP: debug window
					setNextWindowSize(400f, 200f)
					begin("Rendering info")
					text("display size: ${Imgui.io.displaySize.width} x ${Imgui.io.displaySize.height}")
					text("frame time: ${String.format("%.3f", 1000f*Imgui.io.deltaTime)} ms")
					text("FPS: ${String.format("%.3f", Imgui.io.frameRate)}")
					end()

					// draw the slides on the window
					for (info in slides.values) {
						begin(info.slide.name)
						image(info.imageDesc)
						end()
					}
				}
			}
		}

		// wait for the device to finish before starting cleanup
		renderer.waitForIdle()
	}


	inner class SlideInfo(val slide: Slide): AutoCloseable {

		private val closer = AutoCloser()
		private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
		override fun close() = closer.close()

		val renderer = SlideRenderer(
			this@WindowThread.renderer.device,
			this@WindowThread.renderer.graphicsFamily,
			320,
			240
		).autoClose()

		val semaphore = renderer.device.semaphore().autoClose()

		val imageDesc = Imgui.imageDescriptor(renderer.imageView, renderer.imageSampler).autoClose()
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
