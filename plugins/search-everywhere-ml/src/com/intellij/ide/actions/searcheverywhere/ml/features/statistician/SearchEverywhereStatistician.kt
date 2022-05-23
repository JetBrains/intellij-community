package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SearchEverywhereStatistician<T : Any>(private vararg val supportedClasses: Class<out T>)
  : Statistician<Any, String>() {
  protected val contextPrefix = "searchEverywhere"

  abstract fun getContext(element: T): String?

  override fun serialize(element: Any, location: String): StatisticsInfo? {
    @Suppress("UNCHECKED_CAST")
    if (isElementSupported(element)) return serializeElement(element as T, location)
    return null
  }

  abstract fun serializeElement(element: T, location: String): StatisticsInfo?

  private fun isElementSupported(element: Any) = supportedClasses.any { it.isAssignableFrom(element::class.java) }
}
