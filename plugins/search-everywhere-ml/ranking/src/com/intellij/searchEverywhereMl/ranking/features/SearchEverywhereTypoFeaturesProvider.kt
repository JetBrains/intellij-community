package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereTypoFeaturesProvider.Fields.SUGGESTION_CONFIDENCE_FIELD

private class SearchEverywhereTypoFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(SearchEverywhereSpellingCorrectorContributor::class.java) {
  object Fields {
    val SUGGESTION_CONFIDENCE_FIELD = EventFields.Float("suggestionConfidence")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(SUGGESTION_CONFIDENCE_FIELD)

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val correction = element as? SearchEverywhereSpellCheckResult.Correction ?: return emptyList()
    return listOf(SUGGESTION_CONFIDENCE_FIELD.with(correction.confidence.toFloat()))
  }
}
