package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicReference

class DiskSynchronizedEmbeddingsStorageSetup<T : IndexableEntity>(
  private val index: DiskSynchronizedEmbeddingSearchIndex,
  private val indexingTaskManager: EmbeddingIndexingTaskManager,
  private val indexableEntities: List<T>,
  private val setupTaskIndicator: AtomicReference<ProgressIndicator>,
  @Nls private val setupTitle: String
) {
  private var shouldSaveToDisk = true

  fun run(indicator: ProgressIndicator) {
    if (checkEmbeddingsReady(indexableEntities)) {
      shouldSaveToDisk = false
      return
    }
    // Cancel the previous embeddings calculation task if it's not finished
    setupTaskIndicator.getAndSet(indicator)?.cancel()

    indicator.text = setupTitle
    var indexedEntitiesCount = index.size
    val totalIndexableEntitiesCount = indexableEntities.size
    indicator.isIndeterminate = false

    indexableEntities.asSequence()
      .filter { it.id !in index }
      .chunked(BATCH_SIZE)
      .forEach { batch ->
        ProgressManager.checkCanceled()
        val ids = batch.map { it.id.intern() }
        val texts = batch.map { it.indexableRepresentation }
        indexingTaskManager.scheduleTask(EmbeddingIndexingTask.Add(ids, texts) {
          indicator.checkCanceled()
          indexedEntitiesCount += ids.size
          indicator.fraction = indexedEntitiesCount.toDouble() / totalIndexableEntitiesCount
        })
      }
    indexingTaskManager.waitUntilReady()

    // If the indicator is already changed, then the current task is already canceled
    if (setupTaskIndicator.compareAndSet(indicator, null)) indicator.cancel()
  }

  fun onFinish() {
    indexingTaskManager.cancelIndexTasks()
    if (shouldSaveToDisk) ApplicationManager.getApplication().executeOnPooledThread { index.saveToDisk() }
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