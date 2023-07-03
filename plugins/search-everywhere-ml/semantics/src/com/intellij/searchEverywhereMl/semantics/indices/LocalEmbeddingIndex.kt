package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.searchEverywhereMl.semantics.services.ScoredText
import com.intellij.util.containers.CollectionFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LocalEmbeddingIndex(root: Path) {
  private var idToEmbedding: MutableMap<String, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val lock = ReentrantReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  val size: Int get() = lock.read { idToEmbedding.size }
  operator fun contains(id: String): Boolean = lock.read { id in idToEmbedding }

  fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double? = null): List<ScoredText> = lock.read {
    idToEmbedding.mapValues { searchEmbedding.times(it.value) }
      .filter { (_, similarity) -> if (similarityThreshold != null) similarity > similarityThreshold else true }
      .toList()
      .sortedByDescending { (_, similarity) -> similarity }
      .take(topK)
      .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
  }

  fun saveToDisk() = lock.read { fileManager.saveIndex(idToEmbedding) }
  fun loadFromDisk() = lock.write { idToEmbedding = fileManager.loadIndex() }

  fun addValues(values: Iterable<Pair<String, FloatTextEmbedding>>) = lock.write { idToEmbedding.putAll(values) }
}