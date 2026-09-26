package com.intellij.searchEverywhereMl.ranking.core.features.statistician

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class SearchEverywhereStatistician<T : Any>(private vararg val supportedClasses: Class<out T>)
  : Statistician<Any, String>() {
  protected val contextPrefix: String = "searchEverywhere"
  protected open val requiresReadAction: Boolean = false

  abstract fun getContext(element: T): String?

  abstract fun getValue(element: T, location: String): String?

  override fun serialize(element: Any, location: String): StatisticsInfo? {
    if (!isElementSupported(element)) return null

    @Suppress("UNCHECKED_CAST")
    element as T

    if (requiresReadAction) {
      return runReadActionBlocking { getStatisticsInfo(element, location) }
    }

    return getStatisticsInfo(element, location)
  }

  private fun getStatisticsInfo(element: T, location: String): StatisticsInfo? {
    val context = getContext(element) ?: return null
    val value = getValue(element, location) ?: return null

    return StatisticsInfo(context, value)
  }

  private fun isElementSupported(element: Any) = supportedClasses.any { it.isAssignableFrom(element::class.java) }
}
