package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.services.SymbolEmbeddingStorage
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

class SemanticSymbolsProvider(private val project: Project, override var model: FilteringGotoByModel<*>) : SemanticPsiItemsProvider {
  override fun getEmbeddingsStorage() = SymbolEmbeddingStorage.getInstance(project)

  override fun isEnabled() = SearchEverywhereSemanticSettings.getInstance().enabledInSymbolsTab
}