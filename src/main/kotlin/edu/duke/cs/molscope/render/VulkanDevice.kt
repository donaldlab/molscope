package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.AutoCloser
import cuchaz.kludge.tools.IntFlags
import cuchaz.kludge.tools.toFlagsString
import cuchaz.kludge.vulkan.*
import edu.duke.cs.molscope.Molscope


class VulkanDevice(
	val vulkanExtensions: Set<String> = emptySet()
) : AutoCloseable {

	private val autoCloser = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { autoCloser.add(this@autoClose) }
	override fun close() = autoCloser.close()

	private val canDebug = Molscope.dev && Vulkan.DebugExtension in Vulkan.supportedExtensions

	// make the main vulkan instance with the extensions we need
	val vulkan =
		Vulkan(
			extensionNames = vulkanExtensions.toMutableSet().apply {
				if (canDebug) {
					add(Vulkan.DebugExtension)
				}
			},
			layerNames = if (Molscope.dev && Vulkan.StandardValidationLayer in Vulkan.supportedLayers) {
				setOf(Vulkan.StandardValidationLayer)
			} else {
				emptySet()
			}
		)
		.autoClose()
		.apply {
			if (canDebug) {
				// listen to problems from vulkan
				debugMessenger(
					severities = IntFlags.of(DebugMessenger.Severity.Error, DebugMessenger.Severity.Warning)
				) { severity, type, msg ->
					println("VULKAN: ${severity.toFlagsString()} ${type.toFlagsString()} $msg")
					Exception("Stack Trace").printStackTrace(System.out)
				}.autoClose()
			}
		}

	// pick a physical device: prefer discrete GPU
	// TODO: do we want to pick the physical device that is rendering the desktop?
	val physicalDevice = vulkan.physicalDevices
		.asSequence()
		.sortedBy { if (it.properties.type == PhysicalDevice.Type.DiscreteGpu) 0 else 1 }
		.first()

	val deviceFeatures = PhysicalDevice.Features().apply {
		geometryShader = true
		independentBlend = true
		shaderStorageImageExtendedFormats = true
	}
}
