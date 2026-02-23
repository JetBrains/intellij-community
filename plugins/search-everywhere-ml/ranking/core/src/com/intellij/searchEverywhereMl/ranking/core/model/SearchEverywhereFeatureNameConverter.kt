// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.model

// This conversion is required while bundled models still expect legacy camelCase names.
// It can be removed after models are re-trained with snake_case feature names.
private val LEGACY_FEATURE_NAME_ALIASES = mapOf(
  "action_plugin_type" to "pluginType",
  "is_in_library" to "isFromLibrary",
  "max_usage_se" to "maxUsageSE",
  "min_usage_se" to "minUsageSE",
)

internal fun withLegacyFeatureNameAliases(features: Map<String, Any?>): Map<String, Any?> {
  val featuresWithLegacyAliases = HashMap<String, Any?>(features.size * 2)
  for ((featureName, value) in features) {
    featuresWithLegacyAliases[featureName] = value
    featuresWithLegacyAliases.putIfAbsent(toLegacyFeatureName(featureName), value)
  }
  return featuresWithLegacyAliases
}

internal fun toLegacyFeatureName(featureName: String): String {
  return LEGACY_FEATURE_NAME_ALIASES[featureName] ?: featureName.snakeCaseToCamelCase()
}

private fun String.snakeCaseToCamelCase(): String {
  if (indexOf('_') < 0) return this

  val result = StringBuilder(length)
  var uppercaseNext = false
  for (char in this) {
    if (char == '_') {
      uppercaseNext = true
      continue
    }

    result.append(if (uppercaseNext) char.uppercaseChar() else char)
    uppercaseNext = false
  }
  return result.toString()
}
