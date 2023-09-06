package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage

class LocalSemanticActionsProvider(model: GotoActionModel) : SemanticActionsProvider(model) {
  override fun search(pattern: String, similarityThreshold: Double?): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptyList()
    return ActionEmbeddingsStorage.getInstance()
      .searchNeighbours(pattern, ITEMS_LIMIT, similarityThreshold)
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
  }

  override fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptySequence()
    return ActionEmbeddingsStorage.getInstance()
      .streamSearchNeighbours(pattern, similarityThreshold)
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
  }

  companion object {
    private const val ITEMS_LIMIT = 10
  }
}