package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface StreamSemanticItemsProvider<I> : SemanticItemsProvider<I> {
  suspend fun streamSearch(pattern: String, similarityThreshold: Double? = null): Flow<ScoredText>

  suspend fun streamSearchIfEnabled(pattern: String, similarityThreshold: Double? = null): Flow<ScoredText> {
    return if (isEnabled()) streamSearch(pattern, similarityThreshold) else emptyFlow()
  }
}