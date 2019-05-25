package edu.duke.cs.molscope.gui.features

import java.util.*


class FeatureId(menu: String, name: String) {

	private fun String.normalize() = toLowerCase()

	val menu: String = menu.normalize()
	val name: String = name.normalize()

	override fun toString() = "$menu/$name"
	override fun hashCode() = Objects.hash(menu, name)
	override fun equals(other: Any?) =
		other is FeatureId
			&& this.menu == other.menu
			&& this.name == other.name
}
