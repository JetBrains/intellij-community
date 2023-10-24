package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings

class LocalSemanticActionsProvider(model: GotoActionModel) : SemanticActionsProvider(model) {
  override suspend fun search(pattern: String, similarityThreshold: Double?): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptyList()
    return ActionEmbeddingsStorage.getInstance()
      .searchNeighboursIfEnabled(pattern, ITEMS_LIMIT, similarityThreshold)
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptySequence()
    return ActionEmbeddingsStorage.getInstance()
      .streamSearchNeighbours(pattern, similarityThreshold)
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
  }

  override fun isEnabled(): Boolean {
    return SemanticSearchSettings.getInstance().enabledInActionsTab
  }

  companion object {
    private const val ITEMS_LIMIT = 10
  }
}