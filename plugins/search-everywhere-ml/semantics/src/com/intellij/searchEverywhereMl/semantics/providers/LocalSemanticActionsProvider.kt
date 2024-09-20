package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.ml.embeddings.jvm.wrappers.ActionEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.utils.ScoredText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class LocalSemanticActionsProvider(
  model: GotoActionModel,
  presentationProvider: suspend (AnAction) -> Presentation,
) : SemanticActionsProvider(model, presentationProvider) {

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<ScoredText> {
    if (pattern.isBlank()) return emptyList()
    val modelService = LocalEmbeddingServiceProviderImpl.getInstance().getService() ?: return emptyList()
    val embedding = modelService.embed(pattern)
    return ActionEmbeddingsStorageWrapper.getInstance()
      .searchNeighbours(embedding, ITEMS_LIMIT, similarityThreshold)
      .map { (key, similarity) -> ScoredText(key.id, similarity) }
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Flow<ScoredText> {
    if (pattern.isBlank()) return emptyFlow()
    return ActionEmbeddingsStorageWrapper.getInstance()
      .streamSearchNeighbours(pattern, similarityThreshold)
      .map { (key, similarity) -> ScoredText(key.id, similarity) }
  }

  companion object {
    private const val ITEMS_LIMIT = 40
  }
}