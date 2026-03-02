package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.CORRECTION_CONFIDENCE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.IS_SPELL_CHECKED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_POPULAR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_IS_MOST_RECENT_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_RECENCY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereCommonFeaturesProvider.Fields.STATISTICIAN_USE_COUNT_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.SearchEverywhereStatisticianService

internal class SearchEverywhereCommonFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  object Fields {
    internal val PRIORITY_DATA_KEY = EventFields.Int("heuristic_priority")

    internal val STATISTICIAN_USE_COUNT_DATA_KEY = EventFields.Int("stat_use_count")
    internal val STATISTICIAN_IS_MOST_POPULAR_DATA_KEY = EventFields.Boolean("stat_is_most_popular")
    internal val STATISTICIAN_RECENCY_DATA_KEY = EventFields.Int("stat_recency")
    internal val STATISTICIAN_IS_MOST_RECENT_DATA_KEY = EventFields.Boolean("stat_is_most_recent")
    internal val IS_SPELL_CHECKED_DATA_KEY = EventFields.Boolean("is_spell_checked")
    internal val CORRECTION_CONFIDENCE_DATA_KEY = EventFields.Double("correction_confidence")
  }

  override fun isSearchResultsProviderSupported(providerId: String): Boolean = true

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(
      PRIORITY_DATA_KEY,
      STATISTICIAN_USE_COUNT_DATA_KEY, STATISTICIAN_IS_MOST_POPULAR_DATA_KEY,
      STATISTICIAN_RECENCY_DATA_KEY, STATISTICIAN_IS_MOST_RECENT_DATA_KEY,
      IS_SPELL_CHECKED_DATA_KEY, CORRECTION_CONFIDENCE_DATA_KEY
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    if (element is PsiItemWithSimilarity<*>) {
      return getElementFeatures(element.value, currentTime, searchQuery, elementPriority, cache, correction)
    }

    return buildList {
      add(PRIORITY_DATA_KEY.with(elementPriority))
      add(IS_SPELL_CHECKED_DATA_KEY.with(correction is SearchEverywhereSpellCheckResult.Correction))

      if (correction is SearchEverywhereSpellCheckResult.Correction) {
        add(CORRECTION_CONFIDENCE_DATA_KEY.with(correction.confidence))
      }

      addAll(getStatisticianFeatures(element))
    }
  }

  private fun getStatisticianFeatures(element: Any): List<EventPair<*>> {
    val statisticianService = service<SearchEverywhereStatisticianService>()
    val stats = statisticianService.getCombinedStats(element) ?: return emptyList()

    return listOf(
      STATISTICIAN_USE_COUNT_DATA_KEY.with(stats.useCount),
      STATISTICIAN_IS_MOST_POPULAR_DATA_KEY.with(stats.isMostPopular),
      STATISTICIAN_RECENCY_DATA_KEY.with(stats.recency),
      STATISTICIAN_IS_MOST_RECENT_DATA_KEY.with(stats.isMostRecent),
    )
  }
}
