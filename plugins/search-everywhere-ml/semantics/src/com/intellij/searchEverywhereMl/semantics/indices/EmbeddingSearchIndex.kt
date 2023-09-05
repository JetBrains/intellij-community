package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText

interface EmbeddingSearchIndex {
  val size: Int
  var limit: Int?

  operator fun contains(id: String): Boolean

  fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>)

  fun saveToDisk()

  fun loadFromDisk()

  fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double? = null): List<ScoredText>

  fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double? = null): Sequence<ScoredText>

  fun filterIdsTo(idToCount: Map<String, Int>)

  fun checkAllIdsPresent(ids: Set<String>): Boolean

  fun estimateMemoryUsage(): Long

  fun estimateLimitByMemory(memory: Long): Int

  fun checkCanAddEntry(): Boolean
}

internal fun Map<String, FloatTextEmbedding>.findClosest(searchEmbedding: FloatTextEmbedding,
                                                         topK: Int, similarityThreshold: Double?): List<ScoredText> {
  return mapValues { searchEmbedding.times(it.value) }
    .filter { (_, similarity) -> if (similarityThreshold != null) similarity > similarityThreshold else true }
    .toList()
    .sortedByDescending { (_, similarity) -> similarity }
    .take(topK)
    .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
}

internal fun Sequence<Pair<String, FloatTextEmbedding>>.streamFindClose(queryEmbedding: FloatTextEmbedding,
                                                                        similarityThreshold: Double?): Sequence<ScoredText> {
  return map { (id, embedding) -> id to queryEmbedding.times(embedding) }
    .filter { similarityThreshold == null || it.second > similarityThreshold }
    .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
}