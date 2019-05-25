package edu.duke.cs.molscope.view

import cuchaz.kludge.vulkan.ColorRGBA
import java.util.*


enum class ColorsMode {

	Dark,
	Light;

	companion object {

		// start with dark mode by default (can change with window main menu)
		var current = Dark
	}
}

object ColorPalette {

	val darkGrey = Color(
		"Dark Grey",
		dark = ColorRGBA.Int(60, 60, 60),
		light = ColorRGBA.Int(120, 120, 120)
	)

	val lightGrey = Color(
		"Light Grey",
		dark = ColorRGBA.Int(200, 200, 200),
		light = ColorRGBA.Int(220, 220, 220)
	)

	val red = Color(
		"Red",
		dark = ColorRGBA.Int(200, 20, 20),
		light = ColorRGBA.Int(210, 40, 40)
	)

	val blue = Color(
		"Blue",
		dark = ColorRGBA.Int(20, 20, 200),
		light = ColorRGBA.Int(40, 40, 210)
	)

	// TODO: add more colors

	// TODO: refine colors after we get PBR lighting shaders
}


class Color(
	val name: String,
	val shades: Map<ColorsMode,ColorRGBA>
) {

	constructor(name: String, dark: ColorRGBA, light: ColorRGBA) : this(
		name,
		EnumMap<ColorsMode,ColorRGBA>(ColorsMode::class.java).apply {
			this[ColorsMode.Dark] = dark
			this[ColorsMode.Light] = light
		}
	)

	operator fun get(mode: ColorsMode): ColorRGBA =
		shades[mode] ?: throw NoSuchElementException("no color for mode: $mode")
}
