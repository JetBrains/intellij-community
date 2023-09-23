package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor

interface SemanticItemsProvider<I> {
  fun search(pattern: String, similarityThreshold: Double? = null): List<FoundItemDescriptor<I>>

  fun convertCosineSimilarityToInteger(similarityScore: Double): Int {
    return MIN_WEIGHT + (
      (similarityScore - MIN_SIMILARITY_SCORE) / (MAX_SIMILARITY_SCORE - MIN_SIMILARITY_SCORE) * (MAX_WEIGHT - MIN_WEIGHT)).toInt()
  }

  companion object {
    private const val MIN_SIMILARITY_SCORE = -1
    private const val MAX_SIMILARITY_SCORE = 1

    private const val MIN_WEIGHT = -300_000
    private const val MAX_WEIGHT = -100_000
  }
}