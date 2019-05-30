package edu.duke.cs.molscope.gui.features


class Features<T:HasFeatureId> {

	val features = LinkedHashMap<String,LinkedHashMap<String,T>>()

	fun contains(id: FeatureId) =
		(features[id.menu]?.get(id.name)) != null

	fun add(feature: T) {

		// check for duplicates
		if (contains(feature.id)) {
			throw IllegalArgumentException("feature already exists in this window: $feature")
		}

		// add the feature
		features
			.computeIfAbsent(feature.id.menu) { LinkedHashMap() }
			.put(feature.id.name, feature)
	}

	fun remove(id: FeatureId): Boolean {
		val features = features[id.menu] ?: return false
		return features.remove(id.name) != null
	}
}
