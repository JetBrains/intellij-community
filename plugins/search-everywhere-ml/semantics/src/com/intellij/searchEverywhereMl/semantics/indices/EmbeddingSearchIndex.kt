package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.searchEverywhereMl.semantics.services.ScoredText

interface EmbeddingSearchIndex {
  val size: Int

  operator fun contains(id: String): Boolean

  fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>)

  fun saveToDisk()

  fun loadFromDisk()

  fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double? = null): List<ScoredText>

  fun filterIdsTo(ids: Set<String>)

  fun checkAllIdsPresent(ids: Set<String>): Boolean
}

fun Map<String, FloatTextEmbedding>.findClosest(searchEmbedding: FloatTextEmbedding,
                                                topK: Int, similarityThreshold: Double?): List<ScoredText> {
  return mapValues { searchEmbedding.times(it.value) }
    .filter { (_, similarity) -> if (similarityThreshold != null) similarity > similarityThreshold else true }
    .toList()
    .sortedByDescending { (_, similarity) -> similarity }
    .take(topK)
    .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
}