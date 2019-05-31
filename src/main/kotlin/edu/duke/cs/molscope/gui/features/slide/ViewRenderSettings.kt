package edu.duke.cs.molscope.gui.features.slide

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.Ref
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.SlideFeature
import edu.duke.cs.molscope.gui.SlideCommands
import edu.duke.cs.molscope.gui.features.FeatureId


class ViewRenderSettings : SlideFeature(menu, name) {

	companion object {
		const val menu = "View"
		const val name = "Render Settings"
		val id = FeatureId(menu, name)
	}

	val pOpen = Ref.of(false)
	val pLightingWeight = Ref.of(0f)
	val pDepthWeight = Ref.of(0f)
	val pAmbientOcclusionWeight = Ref.of(0f)

	override fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {
		if (menuItem(name)) {
			pOpen.value = true
		}
	}

	override fun gui(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {

		if (pOpen.value) {

			// update references
			pLightingWeight.value = slidewin.renderSettings.lightingWeight
			pDepthWeight.value = slidewin.renderSettings.depthWeight
			pAmbientOcclusionWeight.value = slidewin.renderSettings.ambientOcclusionWeight

			begin("$name##${slide.name}", pOpen, IntFlags.of(Commands.BeginFlags.AlwaysAutoResize))

			// make sliders for the render settings
			if (sliderFloat("Lighting", pLightingWeight, 0f, 1f, "%.1f")) {
				slidewin.renderSettings.lightingWeight = pLightingWeight.value
			}
			if (sliderFloat("Depth Fade", pDepthWeight, 0f, 1f, "%.1f")) {
				slidewin.renderSettings.depthWeight = pDepthWeight.value
			}
			if (sliderFloat("Ambient Occlusion", pAmbientOcclusionWeight, 0f, 1f, "%.1f")) {
				slidewin.renderSettings.ambientOcclusionWeight = pAmbientOcclusionWeight.value
			}

			end()
		}
	}
}
