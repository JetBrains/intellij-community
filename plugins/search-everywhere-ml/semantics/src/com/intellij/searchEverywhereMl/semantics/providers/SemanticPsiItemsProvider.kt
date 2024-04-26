package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.platform.ml.embeddings.search.services.DiskSynchronizedEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.utils.convertNameToNaturalLanguage
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface SemanticPsiItemsProvider : StreamSemanticItemsProvider<PsiItemWithSimilarity<*>> {
  var model: FilteringGotoByModel<*>

  val itemLimit: Int
    get() = ITEM_LIMIT

  fun getEmbeddingsStorage(): DiskSynchronizedEmbeddingsStorage<*>

  override suspend fun search(pattern: String, similarityThreshold: Double?): List<ScoredText> {
    if (pattern.isBlank()) return emptyList()
    return getEmbeddingsStorage().searchNeighbours(convertNameToNaturalLanguage(pattern), itemLimit, similarityThreshold)
  }

  override suspend fun streamSearch(pattern: String,
                                    similarityThreshold: Double?): Flow<ScoredText> {
    if (pattern.isBlank()) return emptyFlow()
    return getEmbeddingsStorage().streamSearchNeighbours(convertNameToNaturalLanguage(pattern), similarityThreshold)
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
  }
}