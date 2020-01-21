package edu.duke.cs.molscope.gui

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
import edu.duke.cs.molscope.Molscope
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.features.FeatureId
import edu.duke.cs.molscope.gui.features.Features
import edu.duke.cs.molscope.gui.features.win.DevFps
import edu.duke.cs.molscope.gui.features.win.DevImguiDemo
import edu.duke.cs.molscope.render.LoadedImage
import edu.duke.cs.molscope.render.VulkanDevice
import edu.duke.cs.molscope.render.WindowRenderer
import edu.duke.cs.molscope.render.toBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger


/**
 * Thread-safe API for the windowing system
 */
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
	private inline fun <R> sync(block: WindowThread.() -> R): R = synchronized(windowThread) { windowThread.block() }

	var backgroundColor: ColorRGBA
		get() = sync { renderer.backgroundColor }
		set(value) { sync { renderer.backgroundColor = value } }


	inner class Slides {

		fun add(slide: Slide) {
			sync {
				addSlide(slide)
			}
		}

		fun remove(slide: Slide) {
			sync {
				removeSlide(slide)
			}
		}
	}
	val slides = Slides()

	inner class WindowFeatures {

		private var nextSeparatorId = AtomicInteger(0)

		fun <R> menu(name: String, block: WindowMenu.() -> R): R {
			sync {
				val menu = features.menu(name)
				return object : WindowMenu {
					override fun add(feature: WindowFeature) = menu.add(feature)
					override fun addSeparator() = menu.add(WindowSeparator(nextSeparatorId.getAndIncrement()))
				}.block()
			}
		}
	}
	val features = WindowFeatures()
}

interface WindowMenu {
	fun add(feature: WindowFeature)
	fun addSeparator()
}

private class WindowSeparator(id: Int) : WindowFeature {

	override val id = FeatureId("WindowSeparator_$id")

	override fun menu(imgui: Commands, win: WindowCommands) = imgui.run {
		separator()
	}
}


/**
 * Thread to manage window, rendering, etc
 */
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
		Imgui.io.apply {
			configWindowsMoveFromTitleBarOnly = true

			// disable saving window positions between runs by default
			iniFilename = null
		}

		// start with dark colors by default (can change in main menu)
		Imgui.styleColors = Imgui.StyleColors.Dark
	}

	var renderer = WindowRenderer(win, vk, device, graphicsQueue, surfaceQueue, surface).autoClose()

	val slideWindows = HashMap<Slide,SlideWindow>()
		.autoClose {
			// cleanup any leftover slides
			for (info in values) {
				info.close()
			}
			clear()
		}

	val slideWindowsToClose = ArrayList<SlideWindow>()

	private fun closeDeferredSlideWindows() {
		for (win in slideWindowsToClose) {
			win.close()
		}
		slideWindowsToClose.clear()
	}

	fun addSlide(slide: Slide) {
		if (!slideWindows.containsKey(slide)) {
			slideWindows[slide] = SlideWindow(slide, graphicsQueue, exceptionViewer)
		}
	}

	fun removeSlide(slide: Slide): Boolean {
		val win = slideWindows.remove(slide) ?: return false

		// defer cleanup of the slide window until after the current frame, otherwise Vulkan will complain
		slideWindowsToClose.add(win)

		return true
	}

	val features = Features<WindowFeature>().apply {

		// add dev-only features if desired
		if (Molscope.dev) {
			menu("Dev").apply {
				add(DevFps())
				add(DevImguiDemo())
			}
		}
	}

	private val exceptionViewer = ExceptionViewer()

	private val winCommands = object : WindowCommands {

		override fun showExceptions(block: () -> Unit) {
			try {
				block()
			} catch (t: Throwable) {
				t.printStackTrace(System.err)
				exceptionViewer.add(t)
			}
		}

		override var shouldClose: Boolean
			get() = win.shouldClose
			set(value) { win.shouldClose = value }

		override fun addSlide(slide: Slide) = this@WindowThread.addSlide(slide)
		override fun removeSlide(slide: Slide): Boolean = this@WindowThread.removeSlide(slide)

		override fun loadImage(bytes: ByteArray) =
			LoadedImage(graphicsQueue, bytes.toBuffer())
				.autoClose()
	}


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

						if (beginMainMenuBar()) {

							// render window feature menus
							for (menu in features.menus) {
								if (beginMenu(menu.name)) {
									for (feature in menu.features) {
										feature.menu(this, winCommands)
									}
									endMenu()
								}
							}

							endMainMenuBar()
						}

						// render window feature guis
						for (menu in features.menus) {
							for (feature in menu.features) {
								feature.gui(this, winCommands)
							}
						}

						// draw the slide sub-windows
						for (slidewin in slideWindows.values) {
							slidewin.gui(this)
						}

						// render exceptions
						exceptionViewer.gui(this)
					}

					// cleanup any deferred slide windows
					if (slideWindowsToClose.isNotEmpty()) {

						// wait for the frame to finish first though
						device.waitForIdle()

						closeDeferredSlideWindows()
					}

				} catch (ex: SwapchainOutOfDateException) {

					device.waitForIdle()

					// cleanup any deferred slide windows
					closeDeferredSlideWindows()

					// re-create the renderer
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
		device.waitForIdle()
	}
}
