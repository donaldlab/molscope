package edu.duke.cs.molscope.gui.features.slide

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.Ref
import edu.duke.cs.molscope.Slide
import edu.duke.cs.molscope.gui.SlideFeature
import edu.duke.cs.molscope.gui.SlideCommands
import edu.duke.cs.molscope.gui.features.FeatureId


class MenuRenderSettings : SlideFeature {

	override val id = FeatureId("rendersettings")

	val pOpen = Ref.of(false)

	override fun menu(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {
		if (menuItem("Render Settings")) {
			pOpen.value = true
		}
	}

	override fun gui(imgui: Commands, slide: Slide.Locked, slidewin: SlideCommands) = imgui.run {

		if (pOpen.value) {

			begin("Render Settings##${slide.name}", pOpen, IntFlags.of(Commands.BeginFlags.AlwaysAutoResize))

			// make sliders for the render settings
			sliderFloat("Color", Ref.of(slidewin.renderSettings::colorWeight), 0f, 1f, "%.2f")
			sliderFloat("Shading", Ref.of(slidewin.renderSettings::shadingWeight), 0f, 2f, "%.2f")
			sliderFloat("Light Intensity", Ref.of(slidewin.renderSettings::lightWeight), 0f, 4f, "%.2f")
			sliderFloat("Depth Fade", Ref.of(slidewin.renderSettings::depthWeight), 0f, 1f, "%.2f")
			sliderFloat("Ambient Occlusion", Ref.of(slidewin.renderSettings::ambientOcclusionWeight), 0f, 4f, "%.2f")

			end()
		}
	}
}
