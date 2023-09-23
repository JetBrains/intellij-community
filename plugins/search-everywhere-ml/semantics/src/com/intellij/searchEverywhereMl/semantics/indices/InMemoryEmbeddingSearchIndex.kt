package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.util.containers.CollectionFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Concurrent [EmbeddingSearchIndex] that stores all embeddings in the memory and allows
 * simultaneous read operations from multiple consumers.
 * Can be persisted to disk.
 */
class InMemoryEmbeddingSearchIndex(root: Path, limit: Int? = null) : EmbeddingSearchIndex {
  private var idToEmbedding: MutableMap<String, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val lock = ReentrantReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override var limit = limit
    set(value) = lock.write {
      // Shrink index if necessary:
      if (value != null && value < idToEmbedding.size) {
        idToEmbedding = idToEmbedding.toList().take(value).toMap().toMutableMap()
      }
      field = value
    }

  override val size: Int get() = lock.read { idToEmbedding.size }

  override operator fun contains(id: String): Boolean = lock.read { id in idToEmbedding }

  override fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>) = lock.write {
    if (limit != null) {
      val list = values.toList()
      idToEmbedding.putAll(list.take(minOf(limit!! - idToEmbedding.size, list.size)))
    }
    else {
      idToEmbedding.putAll(values)
    }
  }

  override fun saveToDisk() = lock.read { save() }

  override fun loadFromDisk() = lock.write {
    val (ids, embeddings) = fileManager.loadIndex() ?: return
    idToEmbedding = (ids zip embeddings).toMap().toMutableMap()
  }

  override fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    return idToEmbedding.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Sequence<ScoredText> = lock.read {
    return idToEmbedding.asSequence().map { it.key to it.value }.streamFindClose(searchEmbedding, similarityThreshold)
  }

  override fun filterIdsTo(idToCount: Map<String, Int>) = lock.write {
    val oldSize = idToEmbedding.size
    val uniqueIds = idToCount.keys
    idToEmbedding = idToEmbedding.filterKeys { it in uniqueIds }.toMutableMap()
    if (idToEmbedding.size != oldSize) save()
  }

  override fun checkAllIdsPresent(ids: Set<String>): Boolean = lock.read {
    return ids.all { it in idToEmbedding } || !checkCanAddEntry()
  }

  override fun estimateMemoryUsage() = fileManager.embeddingSizeInBytes.toLong() * size

  override fun estimateLimitByMemory(memory: Long): Int {
    return (memory / fileManager.embeddingSizeInBytes).toInt()
  }

  override fun checkCanAddEntry(): Boolean = lock.read {
    return limit == null || idToEmbedding.size < limit!!
  }

  private fun save() {
    val (ids, embeddings) = idToEmbedding.toList().unzip()
    fileManager.saveIndex(ids, embeddings)
  }
}