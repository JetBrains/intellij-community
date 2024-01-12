package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.ml.embeddings.search.services.ActionEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.utils.ScoredText

class LocalSemanticActionsProvider(
  model: GotoActionModel,
  presentationProvider: suspend (AnAction) -> Presentation
) : SemanticActionsProvider(model, presentationProvider) {

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<ScoredText> {
    if (pattern.isBlank()) return emptyList()
    return ActionEmbeddingsStorage.getInstance().searchNeighbours(pattern, ITEMS_LIMIT, similarityThreshold)
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<ScoredText> {
    if (pattern.isBlank()) return emptySequence()
    return ActionEmbeddingsStorage.getInstance().streamSearchNeighbours(pattern, similarityThreshold)
  }

  companion object {
    private const val ITEMS_LIMIT = 40
  }
}