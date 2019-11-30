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

fun <K,V> identityHashMapOf(vararg pairs: Pair<K,V>) =
	IdentityHashMap<K,V>().apply {
		putAll(pairs)
	}

fun <T> identityHashSetOf(vararg values: T) =
	identityHashSet<T>().apply {
		addAll(values)
	}

fun <T,K,V> Iterable<T>.associateIdentity(transform: (T) -> Pair<K,V>): MutableMap<K,V> {
	return associateTo(IdentityHashMap<K,V>(), transform)
}

fun <K1,K2,V> Map<K1,V>.mapKeysIdentity(transform: (Map.Entry<K1,V>) -> K2): MutableMap<K2,V> {
	return mapKeysTo(IdentityHashMap<K2,V>(), transform)
}

fun <K,V1,V2> Map<K,V1>.mapValuesIdentity(transform: (Map.Entry<K,V1>) -> V2): MutableMap<K,V2> {
	return mapValuesTo(IdentityHashMap<K,V2>(), transform)
}
