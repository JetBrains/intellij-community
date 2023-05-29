package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor

interface SemanticItemsProvider<I> {
  fun search(pattern: String): List<FoundItemDescriptor<I>>
}