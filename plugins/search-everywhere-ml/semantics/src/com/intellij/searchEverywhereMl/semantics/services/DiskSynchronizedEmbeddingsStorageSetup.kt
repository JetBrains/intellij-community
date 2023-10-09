package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.progress.*
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.utils.LowMemoryNotificationManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class DiskSynchronizedEmbeddingsStorageSetup<T : IndexableEntity>(
  private val index: DiskSynchronizedEmbeddingSearchIndex,
  private val indexSetupJob: AtomicReference<Job>,
  private val indexableEntities: List<T>,
) {
  private var shouldSaveToDisk = true

  suspend fun run() = coroutineScope {
    if (checkEmbeddingsReady(indexableEntities)) {
      shouldSaveToDisk = false
      return@coroutineScope
    }

    indexSetupJob.getAndSet(launch {
      indexableEntities
        .filter { it.id !in index }
        .chunked(BATCH_SIZE)
        .forEachWithProgress(concurrent = false) { batch ->
          if (index.checkCanAddEntry()) {
            val ids = batch.map { it.id.intern() }
            val texts = batch.map { it.indexableRepresentation }
            EmbeddingIndexingTask.Add(ids, texts).run(index)
          } else {
            LowMemoryNotificationManager.getInstance().showNotification()
          }
        }
    })?.cancel()
  }

  fun onFinish(cs: CoroutineScope) {
    indexSetupJob.set(null)
    if (shouldSaveToDisk) {
      cs.launch(Dispatchers.IO) {
        index.saveToDisk()
      }
    }
  }

  private fun checkEmbeddingsReady(indexableEntities: List<IndexableEntity>): Boolean {
    val idToCount = indexableEntities.groupingBy { it.id.intern() }.eachCount()
    index.filterIdsTo(idToCount)
    return index.checkAllIdsPresent(idToCount.keys)
  }

  companion object {
    private const val BATCH_SIZE = 1
  }
}