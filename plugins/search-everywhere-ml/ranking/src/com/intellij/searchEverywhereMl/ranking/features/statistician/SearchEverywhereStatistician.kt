package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SearchEverywhereStatistician<T : Any>(private vararg val supportedClasses: Class<out T>)
  : Statistician<Any, String>() {
  protected val contextPrefix = "searchEverywhere"

  abstract fun getContext(element: T): String?

  abstract fun getValue(element: T, location: String): String?

  override fun serialize(element: Any, location: String): StatisticsInfo? {
    if (!isElementSupported(element)) return null

    @Suppress("UNCHECKED_CAST")
    element as T

    val context = getContext(element) ?: return null
    val value = getValue(element, location) ?: return null

    return StatisticsInfo(context, value)
  }

  private fun isElementSupported(element: Any) = supportedClasses.any { it.isAssignableFrom(element::class.java) }
}
