package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.ranking.features.statistician.SearchEverywhereStatisticianService

internal class SearchEverywhereCommonFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    internal val PRIORITY_DATA_KEY = EventFields.Int("priority")

    internal val STATISTICIAN_USE_COUNT_DATA_KEY = EventFields.Int("statUseCount")
    internal val STATISTICIAN_IS_MOST_POPULAR_DATA_KEY = EventFields.Boolean("statIsMostPopular")
    internal val STATISTICIAN_RECENCY_DATA_KEY = EventFields.Int("statRecency")
    internal val STATISTICIAN_IS_MOST_RECENT_DATA_KEY = EventFields.Boolean("statIsMostRecent")
  }

  override fun isContributorSupported(contributorId: String): Boolean = true

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(
      PRIORITY_DATA_KEY,
      STATISTICIAN_USE_COUNT_DATA_KEY, STATISTICIAN_IS_MOST_POPULAR_DATA_KEY,
      STATISTICIAN_RECENCY_DATA_KEY, STATISTICIAN_IS_MOST_RECENT_DATA_KEY,
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    if (element is PsiItemWithSimilarity<*>) {
      return getElementFeatures(element.value, currentTime, searchQuery, elementPriority, cache)
    }
    val features = arrayListOf<EventPair<*>>(
      PRIORITY_DATA_KEY.with(elementPriority),
    )
    addStatisticianFeatures(element, features)
    return features
  }

  private fun addStatisticianFeatures(element: Any, features: MutableList<EventPair<*>>) {
    val statisticianService = service<SearchEverywhereStatisticianService>()

    statisticianService.getCombinedStats(element)?.let { stats ->
      features.add(STATISTICIAN_USE_COUNT_DATA_KEY.with(stats.useCount))
      features.add(STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(stats.isMostPopular))
      features.add(STATISTICIAN_RECENCY_DATA_KEY.with(stats.recency))
      features.add(STATISTICIAN_IS_MOST_RECENT_DATA_KEY.with(stats.isMostRecent))
    }
  }
}
