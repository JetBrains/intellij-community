package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.utils.ScoredText
import com.intellij.platform.ml.embeddings.utils.convertNameToNaturalLanguage
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class LocalSemanticActionsProvider(
  model: GotoActionModel,
  presentationProvider: suspend (AnAction) -> Presentation,
) : SemanticActionsProvider(model, presentationProvider) {

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<ScoredText> {
    if (pattern.isBlank()) return emptyList()
    val searchStart = System.nanoTime()
    val result = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.ACTIONS)
      .search(null, convertNameToNaturalLanguage(pattern), ITEMS_LIMIT, similarityThreshold?.toFloat())
    EmbeddingSearchLogger.searchFinished(null, IndexId.ACTIONS, TimeoutUtil.getDurationMillis(searchStart))
    return result
  }

  override suspend fun streamSearch(pattern: String, similarityThreshold: Double?): Flow<ScoredText> {
    return search(pattern, similarityThreshold).asFlow()
  }

  companion object {
    private const val ITEMS_LIMIT = 40
  }
}