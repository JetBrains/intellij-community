package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor

interface StreamSemanticItemsProvider<I>: SemanticItemsProvider<I> {
  suspend fun streamSearch(pattern: String, similarityThreshold: Double? = null): Sequence<FoundItemDescriptor<I>>

  suspend fun streamSearchIfEnabled(pattern: String, similarityThreshold: Double? = null): Sequence<FoundItemDescriptor<I>> {
    if (isEnabled()) {
      return streamSearch(pattern, similarityThreshold)
    }
    return emptySequence()
  }
}