package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.platform.ml.embeddings.search.services.DiskSynchronizedEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import com.intellij.util.concurrency.ThreadingAssertions

interface SemanticPsiItemsProvider : StreamSemanticItemsProvider<PsiItemWithSimilarity<*>> {
  var model: FilteringGotoByModel<*>

  val itemLimit: Int
    get() = ITEM_LIMIT

  fun getEmbeddingsStorage(): DiskSynchronizedEmbeddingsStorage<*>

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<ScoredText> {
    if (pattern.isBlank()) return emptyList()
    return getEmbeddingsStorage().searchNeighbours(convertPatternToNaturalLanguage(pattern), itemLimit, similarityThreshold)
  }

  override suspend fun streamSearch(pattern: String,
                                    similarityThreshold: Double?): Sequence<ScoredText> {
    if (pattern.isBlank()) return emptySequence()
    return getEmbeddingsStorage().streamSearchNeighbours(convertPatternToNaturalLanguage(pattern), similarityThreshold)
  }

  override suspend fun createItemDescriptors(name: String,
                                             similarityScore: Double,
                                             pattern: String): List<FoundItemDescriptor<PsiItemWithSimilarity<*>>> {
    ThreadingAssertions.assertReadAccess()
    val shiftedScore = convertCosineSimilarityToInteger(similarityScore)
    return model.getElementsByName(name, false, pattern)
      .map { FoundItemDescriptor(PsiItemWithSimilarity(it, similarityScore), shiftedScore) }
  }

  companion object {
    private const val ITEM_LIMIT = 50

    fun convertPatternToNaturalLanguage(pattern: String) = splitIdentifierIntoTokens(pattern).joinToString(" ")
  }
}