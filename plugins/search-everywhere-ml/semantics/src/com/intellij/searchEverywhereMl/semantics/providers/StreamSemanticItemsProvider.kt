package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor

interface StreamSemanticItemsProvider<I>: SemanticItemsProvider<I> {
  fun streamSearch(pattern: String, similarityThreshold: Double? = null): Sequence<FoundItemDescriptor<I>>
}