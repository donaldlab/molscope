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

inline fun <R> Commands.styleEnabledIf(isEnabled: Boolean, block: () -> R) =
	styleDisabledIf(!isEnabled, block)


interface WithColumns {
	fun column(width: Float? = null, block: () -> Unit)
}
fun Commands.columns(num: Int, border: Boolean = false, block: WithColumns.() -> Unit) {
	columns(num, border = border)
	try {
		var offset = 0f
		var i = 0
		object : WithColumns {
			override fun column(width: Float?, block: () -> Unit) {
				child("column$i", block = block)
				i += 1
				if (width != null) {
					offset += width
					setColumnOffset(i, offset)
				}
				nextColumn()
			}
		}.block()
	} finally {
		columns(1)
	}
}
