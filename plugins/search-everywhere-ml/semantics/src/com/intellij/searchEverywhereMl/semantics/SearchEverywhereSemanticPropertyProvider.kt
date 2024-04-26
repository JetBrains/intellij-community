package com.intellij.searchEverywhereMl.semantics

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereSessionPropertyProvider
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking
import com.intellij.searchEverywhereMl.semantics.SearchEverywhereSemanticPropertyProvider.Fields.SEMANTIC_EXPERIMENT_GROUP
import com.intellij.searchEverywhereMl.semantics.SearchEverywhereSemanticPropertyProvider.Fields.SEMANTIC_SEARCH_ENABLED
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType.ENABLE_SEMANTIC_SEARCH

class SearchEverywhereSemanticPropertyProvider : SearchEverywhereSessionPropertyProvider() {
  object Fields {
    val SEMANTIC_EXPERIMENT_GROUP = EventFields.Int("semanticExperimentGroup")
    val SEMANTIC_SEARCH_ENABLED = EventFields.Boolean("semanticSearchEnabled")
  }

  override fun getDeclarations(): List<EventField<*>> = arrayListOf(
    SEMANTIC_EXPERIMENT_GROUP, SEMANTIC_SEARCH_ENABLED
  )

  override fun getProperties(tabId: String): List<EventPair<*>> {
    return arrayListOf(
      SEMANTIC_EXPERIMENT_GROUP.with(SearchEverywhereMlExperiment().experimentGroup),
      SEMANTIC_SEARCH_ENABLED.with(SearchEverywhereMlExperiment().getExperimentForTab(
        SearchEverywhereTabWithMlRanking.findById(tabId)!!) == ENABLE_SEMANTIC_SEARCH)
    )
  }
}