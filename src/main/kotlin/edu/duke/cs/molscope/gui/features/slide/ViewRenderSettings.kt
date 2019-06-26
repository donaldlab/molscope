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
	val pColorWeight = Ref.of(0f)
	val pLightWeight = Ref.of(0f)
	val pShadingWeight = Ref.of(0f)
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
			pColorWeight.value = slidewin.renderSettings.colorWeight
			pShadingWeight.value = slidewin.renderSettings.shadingWeight
			pLightWeight.value = slidewin.renderSettings.lightWeight
			pDepthWeight.value = slidewin.renderSettings.depthWeight
			pAmbientOcclusionWeight.value = slidewin.renderSettings.ambientOcclusionWeight

			begin("$name##${slide.name}", pOpen, IntFlags.of(Commands.BeginFlags.AlwaysAutoResize))

			// make sliders for the render settings
			if (sliderFloat("Color", pColorWeight, 0f, 1f, "%.2f")) {
				slidewin.renderSettings.colorWeight = pColorWeight.value
			}
			if (sliderFloat("Shading", pShadingWeight, 0f, 1f, "%.2f")) {
				slidewin.renderSettings.shadingWeight = pShadingWeight.value
			}
			if (sliderFloat("Light Intensity", pLightWeight, 0f, 2f, "%.2f")) {
				slidewin.renderSettings.lightWeight = pLightWeight.value
			}
			if (sliderFloat("Depth Fade", pDepthWeight, 0f, 1f, "%.2f")) {
				slidewin.renderSettings.depthWeight = pDepthWeight.value
			}
			if (sliderFloat("Ambient Occlusion", pAmbientOcclusionWeight, 0f, 4f, "%.2f")) {
				slidewin.renderSettings.ambientOcclusionWeight = pAmbientOcclusionWeight.value
			}

			end()
		}
	}
}
