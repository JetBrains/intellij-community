package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatisticianService
import com.intellij.openapi.components.service

class SearchEverywhereCommonFeaturesProvider
  : SearchEverywhereElementFeaturesProvider() {
  companion object {
    internal const val PRIORITY_DATA_KEY = "priority"
    internal const val TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount"

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
    val features = hashMapOf<String, Any>(
      PRIORITY_DATA_KEY to elementPriority,
      TOTAL_SYMBOLS_AMOUNT_DATA_KEY to searchQuery.length,
    )
    addStatisticianFeatures(element, features)
    return features
  }

  private fun addStatisticianFeatures(element: Any, features: MutableMap<String, Any>) {
    val statisticianService = service<SearchEverywhereStatisticianService>()

    statisticianService.getCombinedStats(element)?.let { stats ->
      features[STATISTICIAN_USE_COUNT_DATA_KEY] = stats.useCount
      features[STATISTICIAN_IS_MOST_POPULAR_DATA_KEY] = stats.isMostPopular
      features[STATISTICIAN_RECENCY_DATA_KEY] = stats.recency
      features[STATISTICIAN_IS_MOST_RECENT_DATA_KEY] = stats.isMostRecent
    }
  }
}
