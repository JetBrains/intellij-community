package com.intellij.searchEverywhereMl.semantics

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.SearchEverywhereSessionPropertyProvider
import com.intellij.searchEverywhereMl.semantics.SearchEverywhereSemanticPropertyProvider.Fields.SEMANTIC_EXPERIMENT_GROUP
import com.intellij.searchEverywhereMl.semantics.SearchEverywhereSemanticPropertyProvider.Fields.SEMANTIC_SEARCH_ENABLED
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments

class SearchEverywhereSemanticPropertyProvider: SearchEverywhereSessionPropertyProvider() {
  object Fields {
    val SEMANTIC_EXPERIMENT_GROUP = EventFields.Int("semanticExperimentGroup")
    val SEMANTIC_SEARCH_ENABLED = EventFields.Boolean("semanticSearchEnabled")
  }

  override fun getDeclarations(): List<EventField<*>> = arrayListOf(
    SEMANTIC_EXPERIMENT_GROUP, SEMANTIC_SEARCH_ENABLED
  )

  override fun getProperties(tabId: String): List<EventPair<*>> {
    return arrayListOf(
      SEMANTIC_EXPERIMENT_GROUP.with(SearchEverywhereSemanticExperiments.getInstance ().experimentGroup),
      SEMANTIC_SEARCH_ENABLED.with(isEnabledInTab(tabId, SemanticSearchSettings.getInstance()))
    )
  }

  private fun isEnabledInTab(
    tabId: String,
    settings: SemanticSearchSettings,
  ): Boolean = settings.run {
    return when (tabId) {
      ActionSearchEverywhereContributor::class.java.simpleName ->
        enabledInActionsTab
      FileSearchEverywhereContributor::class.java.simpleName ->
        enabledInFilesTab
      ClassSearchEverywhereContributor::class.java.simpleName ->
        enabledInClassesTab
      SymbolSearchEverywhereContributor::class.java.simpleName ->
        enabledInSymbolsTab
      else -> false
    }
  }
}