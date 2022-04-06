package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo

internal abstract class SearchEverywhereStatistician<T> : Statistician<Any, String>() {
  protected val contextPrefix = "searchEverywhere"

  abstract fun getContext(element: T): String?

  override fun serialize(element: Any, location: String): StatisticsInfo? {
    @Suppress("UNCHECKED_CAST")
    return (element as? T)?.let {
      serializeElement(element, location)
    }
  }

  abstract fun serializeElement(element: T, location: String): StatisticsInfo?
}
