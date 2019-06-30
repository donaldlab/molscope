package edu.duke.cs.molscope.gui

import cuchaz.kludge.imgui.Commands
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.vulkan.ColorRGBA
import edu.duke.cs.molscope.view.ColorsMode


private var nextId = 0

fun Commands.infoTip(block: () -> Unit) {

	pushStyleVar(Commands.StyleVar.WindowPadding, 5f, 2f)
	pushStyleVar(Commands.StyleVar.ChildRounding, 6f)
	pushStyleColor(Commands.StyleColor.Border, ColorRGBA.Int(128, 128, 128))
	pushStyleColor(Commands.StyleColor.Text, ColorRGBA.Int(128, 128, 128))
	pushStyleColor(Commands.StyleColor.ChildBg, when (ColorsMode.current) {
		ColorsMode.Dark -> ColorRGBA.Int(32, 32, 32)
		ColorsMode.Light -> ColorRGBA.Int(220, 220, 220)
	})

	beginChild(
		"infoTip-${nextId++}",
		width = 17f,
		height = 18f,
		border = true,
		flags= IntFlags.of(Commands.BeginFlags.NoScrollbar)
	)
	text("i")
	endChild()

	popStyleVar(2)
	popStyleColor(3)

	if (isItemHovered()) {
		beginTooltip()
		block()
		endTooltip()
	}
}
