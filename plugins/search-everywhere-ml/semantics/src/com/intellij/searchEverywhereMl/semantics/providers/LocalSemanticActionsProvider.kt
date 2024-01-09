package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage

class LocalSemanticActionsProvider(model: GotoActionModel, presentationProvider: suspend (AnAction) -> Presentation) :
  SemanticActionsProvider(model, presentationProvider) {

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptyList()
    return ActionEmbeddingsStorage.getInstance()
      .searchNeighbours(pattern, ITEMS_LIMIT, similarityThreshold)
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<FoundItemDescriptor<GotoActionModel.MatchedValue>> {
    if (pattern.isBlank()) return emptySequence()
    val list = ActionEmbeddingsStorage.getInstance()
      .streamSearchNeighbours(pattern, similarityThreshold)
      .toList()
      .mapNotNull { createItemDescriptor(it.text, it.similarity, pattern) }
    return list.asSequence()
  }

  companion object {
    private const val ITEMS_LIMIT = 10
  }
}