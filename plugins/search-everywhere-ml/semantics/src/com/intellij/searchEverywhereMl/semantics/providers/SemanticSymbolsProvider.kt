package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

class SemanticSymbolsProvider(project: Project) : SemanticPsiItemsProvider(project) {
  override val indexId: IndexId = IndexId.SYMBOLS

  override fun isEnabled() = SearchEverywhereSemanticSettings.getInstance().enabledInSymbolsTab
}