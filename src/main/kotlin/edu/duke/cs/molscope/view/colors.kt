package edu.duke.cs.molscope.view


enum class ColorsMode {

	Dark,
	Light;

	companion object {

		// start with dark mode by default (can change with window main menu)
		var current = ColorsMode.Dark
	}
}

// TODO: make color palettes for light and dark modes?
