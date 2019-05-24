package edu.duke.cs.molscope

import cuchaz.kludge.imgui.Imgui
import cuchaz.kludge.imgui.context
import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.vulkan.*
import cuchaz.kludge.window.Monitors
import cuchaz.kludge.window.Size
import cuchaz.kludge.window.Window as KWindow
import cuchaz.kludge.window.Windows
import edu.duke.cs.molscope.gui.SlideWindow
import edu.duke.cs.molscope.render.VulkanDevice
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
	private fun <R:AutoCloseable> R.autoClose(replace: R? = null) = apply { closer.add(this, replace) }
	private fun <R> R.autoClose(block: R.() -> Unit) = apply { closer.add { block() } }
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

				// render the slides (if possible), and collect the semaphores of the ones we rendered
				val slideSemaphores = slideWindows.values.mapNotNull { slidewin ->
					slidewin.resizeIfNeeded()
					slidewin.slide.lock { slide ->
						if (slidewin.render(slide, slidewin.renderFinished)) {
							return@mapNotNull slidewin.renderFinished
						} else {
							// slide doesn't have size info from the GUI yet
							return@mapNotNull null
						}
					}
				}

				// render the window
				try {
					renderer.render(slideSemaphores) {

						// TEMP: demo window
						//showDemoWindow()

						// draw the slides on the window
						for (slidewin in slideWindows.values) {
							slidewin.gui(this)
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
					renderer = WindowRenderer(win, vk, device, graphicsQueue, surfaceQueue, surface, renderer)
						.autoClose(replace = renderer)

					// re-creating WindowRenderer re-initializes ImGUI, so update the slide image descriptors
					for (info in slideWindows.values) {
						info.updateImageDesc()
					}
				}
			}
		}

		// wait for the device to finish before starting cleanup
		renderer.waitForIdle()
	}

	val slideWindows = HashMap<Slide,SlideWindow>()
		.autoClose {
			// cleanup any leftover slides
			for (info in values) {
				info.close()
			}
			clear()
		}

	fun addSlide(slide: Slide) {
		if (!slideWindows.containsKey(slide)) {
			slideWindows[slide] = SlideWindow(slide, graphicsQueue)
		}
	}

	fun removeSlide(slide: Slide): Boolean {
		val info = slideWindows.remove(slide) ?: return false
		info.close()
		return true
	}
}
