// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlin.math.round

abstract class SearchEverywhereElementFeaturesProvider {
  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList
    }

    fun isElementSupported(element: Any): Boolean {
      return getFeatureProviders().any { it.isElementSupported(element) }
    }
  }

  /**
   * Returns data to be cached in the search session
   */
  open fun getDataToCache(project: Project?): Any? {
    return null
  }

  /**
   * Returns true if the element is supported by the feature provider.
   */
  abstract fun isElementSupported(element: Any): Boolean

  abstract fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int,
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
}