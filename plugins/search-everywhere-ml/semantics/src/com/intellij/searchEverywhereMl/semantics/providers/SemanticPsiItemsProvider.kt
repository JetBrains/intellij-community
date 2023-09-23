package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.searchEverywhereMl.semantics.services.DiskSynchronizedEmbeddingsStorage

interface SemanticPsiItemsProvider : StreamSemanticItemsProvider<PsiItemWithSimilarity<*>> {
  var model: FilteringGotoByModel<*>

  val itemLimit: Int
    get() = ITEM_LIMIT

  fun getEmbeddingsStorage(): DiskSynchronizedEmbeddingsStorage<*>

  override fun search(pattern: String, similarityThreshold: Double?): List<FoundItemDescriptor<PsiItemWithSimilarity<*>>> {
    if (pattern.isBlank()) return emptyList()
    return getEmbeddingsStorage()
      .searchNeighbours(pattern, itemLimit, similarityThreshold)
      .flatMap { createItemDescriptors(it.text, it.similarity, pattern) }
  }

  override fun streamSearch(pattern: String, similarityThreshold: Double?): Sequence<FoundItemDescriptor<PsiItemWithSimilarity<*>>> {
    if (pattern.isBlank()) return emptySequence()
    return getEmbeddingsStorage()
      .streamSearchNeighbours(pattern, similarityThreshold)
      .flatMap { createItemDescriptors(it.text, it.similarity, pattern) }
  }

  private fun createItemDescriptors(name: String,
                                    similarityScore: Double,
                                    pattern: String): List<FoundItemDescriptor<PsiItemWithSimilarity<*>>> {
    val shiftedScore = convertCosineSimilarityToInteger(similarityScore)
    return model.getElementsByName(name, false, pattern)
      .map { FoundItemDescriptor(PsiItemWithSimilarity(it, similarityScore), shiftedScore) }
  }

  companion object {
    private const val ITEM_LIMIT = 10
  }
}