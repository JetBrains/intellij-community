package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

class SemanticClassesProvider(project: Project) : SemanticPsiItemsProvider(project) {
  override val indexId: IndexId = IndexId.CLASSES

  override fun isEnabled(): Boolean = SearchEverywhereSemanticSettings.getInstance().enabledInClassesTab
}