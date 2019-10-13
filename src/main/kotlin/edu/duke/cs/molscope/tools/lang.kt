package edu.duke.cs.molscope.tools

import java.util.*


val Boolean.toYesNo get() = if (this) "Yes" else "No"



private object Assertions {
	internal val Enabled: Boolean = javaClass.desiredAssertionStatus()
}

/**
 * A lazy version of assert() that only evaluates its condition if assertions are enabled.
 */
fun assert(lazyMessage: () -> Any = { "Assertion failed" }, lazyCondition: () -> Boolean) {
	if (Assertions.Enabled) {
		if (!lazyCondition()) {
			throw AssertionError(lazyMessage())
		}
	}
}


fun <T> identityHashSet(): MutableSet<T> =
	Collections.newSetFromMap(IdentityHashMap<T,Boolean>())
