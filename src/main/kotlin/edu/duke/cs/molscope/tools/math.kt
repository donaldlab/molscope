package edu.duke.cs.molscope.tools

import org.joml.AABBf
import org.joml.Vector3f


fun Float.toStringAngstroms() = "%.3f".format(this)

fun Vector3f.toStringAngstroms() = "(%.3f,%.3f,%.3f)".format(x, y, z)

fun AABBf.toStringAngstroms() = "[%.3f,%.3f]x[%.3f,%.3f]x[%.3f,%.3f]".format(minX, maxX, minY, maxY, minZ, maxZ)
