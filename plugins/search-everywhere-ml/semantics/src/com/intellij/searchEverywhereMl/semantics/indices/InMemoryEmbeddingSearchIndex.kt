package com.intellij.searchEverywhereMl.semantics.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.searchEverywhereMl.semantics.services.ScoredText
import com.intellij.util.containers.CollectionFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InMemoryEmbeddingSearchIndex(root: Path): EmbeddingSearchIndex {
  private var idToEmbedding: MutableMap<String, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val lock = ReentrantReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override val size: Int get() = lock.read { idToEmbedding.size }

  override operator fun contains(id: String): Boolean = lock.read { id in idToEmbedding }

  override fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>) = lock.write { idToEmbedding.putAll(values) }

  override fun saveToDisk() = lock.read { save() }

  override fun loadFromDisk() = lock.write {
    idToEmbedding = fileManager.loadIndex()
  }

  override fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    return idToEmbedding.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override fun filterIdsTo(ids: Set<String>) = lock.write {
    val oldSize = idToEmbedding.size
    idToEmbedding = idToEmbedding.filterKeys { it in ids }.toMutableMap()
    if (idToEmbedding.size != oldSize) save()
  }

  override fun checkAllIdsPresent(ids: Set<String>): Boolean = lock.read {
    return ids.all { it in idToEmbedding }
  }

  private fun save() = fileManager.saveIndex(idToEmbedding)
}
