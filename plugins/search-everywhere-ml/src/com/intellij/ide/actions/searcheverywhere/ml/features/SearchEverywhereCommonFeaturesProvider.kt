package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatisticianService
import com.intellij.openapi.components.service

class SearchEverywhereCommonFeaturesProvider
  : SearchEverywhereElementFeaturesProvider() {
  companion object {
    internal const val STATISTICIAN_USE_COUNT_DATA_KEY = "statUseCount"
    internal const val STATISTICIAN_IS_MOST_POPULAR_DATA_KEY = "statIsMostPopular"
    internal const val STATISTICIAN_RECENCY_DATA_KEY = "statRecency"
    internal const val STATISTICIAN_IS_MOST_RECENT_DATA_KEY = "statIsMostRecent"
  }

  override val isApplicableToEveryContributor: Boolean = true

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    return getStatisticianFeatures(element)
  }

  private fun getStatisticianFeatures(element: Any): Map<String, Any> {
    val statisticianService = service<SearchEverywhereStatisticianService>()

    return statisticianService.getCombinedStats(element)?.let { stats ->
      mapOf<String, Any>(
        STATISTICIAN_USE_COUNT_DATA_KEY to stats.useCount,
        STATISTICIAN_IS_MOST_POPULAR_DATA_KEY to stats.isMostPopular,
        STATISTICIAN_RECENCY_DATA_KEY to stats.recency,
        STATISTICIAN_IS_MOST_RECENT_DATA_KEY to stats.isMostRecent,
      )
    } ?: emptyMap()
  }
}
