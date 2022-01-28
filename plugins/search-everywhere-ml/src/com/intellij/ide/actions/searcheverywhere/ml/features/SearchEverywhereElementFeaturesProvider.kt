// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.textMatching.PrefixMatchingUtil
import kotlin.math.round

abstract class SearchEverywhereElementFeaturesProvider(private val supportedContributorIds: List<String>) {
  constructor(vararg supportedTabs: Class<out SearchEverywhereContributor<*>>) : this(supportedTabs.map { it.simpleName })

  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList
    }

    fun getFeatureProvidersForContributor(contributorId: String): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter { it.isContributorSupported(contributorId) }
    }
  }

  /**
   * Returns data to be cached in the search session
   */
  open fun getDataToCache(project: Project?): Any? {
    return null
  }

  /**
   * Returns true if the Search Everywhere contributor is supported by the feature provider.
   */
  fun isContributorSupported(contributorId: String): Boolean {
    return supportedContributorIds.contains(contributorId)
  }

  abstract fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any>

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  internal fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }

  /**
   * Associates the specified key with the value, only if the value is not null.
   */
  protected fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?) {
    value?.let {
      this[key] = it
    }
  }

  protected fun getNameMatchingFeatures(nameOfFoundElement: String, searchQuery: String): Map<String, Any> {
    fun changeToCamelCase(str: String): String {
      val words = str.split('_')
      val firstWord = words.first()
      if (words.size == 1) {
        return firstWord
      } else {
        return firstWord.plus(
          words.subList(1, words.size)
            .joinToString(separator = "") { s -> s.replaceFirstChar { it.uppercaseChar() } }
        )
      }
    }

    val features = mutableMapOf<String, Any>()
    PrefixMatchingUtil.calculateFeatures(nameOfFoundElement, searchQuery, features)
    return features.mapKeys { changeToCamelCase(it.key) }  // Change snake case to camel case for consistency with other feature names.
      .mapValues { if (it.value is Double) roundDouble(it.value as Double) else it.value }
  }
}