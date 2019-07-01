package edu.duke.cs.molscope

import cuchaz.kludge.tools.expand
import cuchaz.kludge.tools.toFloat
import edu.duke.cs.molscope.gui.SlideFeature
import edu.duke.cs.molscope.gui.features.Features
import edu.duke.cs.molscope.gui.features.slide.DevOcclusionField
import edu.duke.cs.molscope.view.RenderView
import org.joml.AABBd
import org.joml.AABBf
import java.util.*
import kotlin.collections.ArrayList


/**
 * a place to stage molecules and geoemtry for viewing
 *
 * slides must be thread-safe since they are directly accessed by the renderer
 */
class Slide(name: String) {

	var name: String = name
		private set

	inner class Locked {

		var name: String
			get() = this@Slide.name
			set(value) { this@Slide.name = value }

		val views: MutableList<RenderView> = ArrayList()

		fun calcBoundingBox(): AABBf? {

			if (views.isEmpty()) {
				return null
			}

			return views
				.map { view -> view.calcBoundingBox() }
				.reduce { a, b -> AABBf().apply { a.union(b, this) } }
		}

		inner class Camera {

			internal val queue = ArrayDeque<CameraCommand>()

			fun lookAtEverything(padding: Double = 1.0) {
				calcBoundingBox()?.let { lookAtBox(it, padding.toFloat()) }
			}

			fun lookAtBox(aabb: AABBf, padding: Float = 1f) {
				queue.addLast(CameraCommand.LookAtBox(aabb.apply { expand(padding) }))
			}

			fun lookAtBox(aabb: AABBd, padding: Double = 1.0) =
				lookAtBox(aabb.toFloat(), padding.toFloat())
		}

		val camera = Camera()

		val features = Features<SlideFeature>().apply {

			// add dev-only features if needed
			if (Molscope.dev) {
				menu("Dev") {
					add(DevOcclusionField())
				}
			}
		}
	}

	/**
	 * lock this slide to prevent races with the renderer
	 *
	 * don't talk to the window while the slide is locked, or you might deadlock!
	 */
	inline fun <R> lock(block: (Locked) -> R): R =
		synchronized(this) {
			block(_locked)
		}

	/**
	 * DO NOT USE THIS WITHOUT SYNCHRONIZING ON SLIDE OR YOU WILL RACE THE RENDER THREAD
	 * Use the lock() convenience method rather than accessing this directly
	 *
	 * If Kotlin/JVM were smarter, this would be private or internal
	 * but as things are now, we can't inline lock() when this is private or internal ;_;
	 */
	val _locked = Locked()
}


internal interface CameraCommand {
	data class LookAtBox(val aabb: AABBf) : CameraCommand
}
