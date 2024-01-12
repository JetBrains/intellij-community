package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.platform.ml.embeddings.search.utils.ScoredText

interface StreamSemanticItemsProvider<I> : SemanticItemsProvider<I> {
  suspend fun streamSearch(pattern: String, similarityThreshold: Double? = null): Sequence<ScoredText>

  suspend fun streamSearchIfEnabled(pattern: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    return if (isEnabled()) streamSearch(pattern, similarityThreshold) else emptySequence()
  }
}