package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereSymbolFeaturesProvider.Fields.PARENT_STAT_IS_MOST_POPULAR_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereSymbolFeaturesProvider.Fields.PARENT_STAT_IS_MOST_RECENT_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereSymbolFeaturesProvider.Fields.PARENT_STAT_RECENCY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereSymbolFeaturesProvider.Fields.PARENT_STAT_USE_COUNT_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.SearchEverywhereStatisticianService

internal class SearchEverywhereSymbolFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(SymbolSearchEverywhereContributor::class.java) {
  object Fields {
    val PARENT_STAT_USE_COUNT_DATA_KEY = EventFields.Int("parentStatUseCount")
    val PARENT_STAT_IS_MOST_POPULAR_DATA_KEY = EventFields.Boolean("parentStatIsMostPopular")
    val PARENT_STAT_RECENCY_DATA_KEY = EventFields.Int("parentStatRecency")
    val PARENT_STAT_IS_MOST_RECENT_DATA_KEY = EventFields.Boolean("parentStatIsMostRecent")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(PARENT_STAT_USE_COUNT_DATA_KEY, PARENT_STAT_IS_MOST_POPULAR_DATA_KEY,
                  PARENT_STAT_RECENCY_DATA_KEY, PARENT_STAT_IS_MOST_RECENT_DATA_KEY)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element)
    return getParentStatisticianFeatures(psiElement)
  }

  private fun getParentStatisticianFeatures(element: PsiElement): List<EventPair<*>> {
    val parent = runReadAction { element.takeIf { it.isValid }?.parent as? PsiNamedElement }
                 ?: return emptyList()
    val service = service<SearchEverywhereStatisticianService>()

    return service.getCombinedStats(parent)?.let { stats ->
      arrayListOf(
        PARENT_STAT_USE_COUNT_DATA_KEY.with(stats.useCount),
        PARENT_STAT_IS_MOST_POPULAR_DATA_KEY.with(stats.isMostPopular),
        PARENT_STAT_RECENCY_DATA_KEY.with(stats.recency),
        PARENT_STAT_IS_MOST_RECENT_DATA_KEY.with(stats.isMostRecent),
      )
    } ?: emptyList()
  }
}
