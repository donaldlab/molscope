package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands


/**
 * Calls `pushStyleVar` to temporarily modify the ImGUI style.
 * Make sure to nest other calls to push/pop StyleVar correctly.
 *
 * ImGUI doesn't have an enabled/disabled system for most controls, so this is a workaround.
 * See: https://github.com/ocornut/imgui/issues/211
 */
fun Commands.pushStyleDisabled() {
	pushStyleVar(Commands.StyleVar.Alpha, 0.4f)
}

fun Commands.popStyleDisabled(num: Int = 1) {
	popStyleVar(num)
}

inline fun <R> Commands.styleDisabledIf(isDisabled: Boolean, block: () -> R): R {
	if (isDisabled) {
		pushStyleDisabled()
	}
	val ret = block()
	if (isDisabled) {
		popStyleDisabled()
	}
	return ret
}
