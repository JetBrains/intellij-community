package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.searchEverywhereMl.semantics.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.withLock

abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project) {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex
  abstract val indexingTaskManager: EmbeddingIndexingTaskManager

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  abstract val setupTitle: String

  abstract fun checkSearchEnabled(): Boolean

  abstract fun getIndexableEntities(): List<T>

  fun prepareForSearch() {
    // Delay embedding indexing until standard indexing is finished
    DumbService.getInstance(project).runWhenSmart {
      ApplicationManager.getApplication().executeOnPooledThread {
        LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
        index.loadFromDisk()
        generateEmbeddingsIfNecessary()
      }
    }
  }

  fun tryStopGeneratingEmbeddings() = setupTaskIndicator.getAndSet(null)?.cancel()

  @RequiresBackgroundThread
  fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText> {
    if (!checkSearchEnabled()) return emptyList()
    val embedding = generateEmbedding(text) ?: return emptyList()
    return index.findClosest(embedding, topK, similarityThreshold)
  }

  @RequiresBackgroundThread
  fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    if (!checkSearchEnabled()) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  fun addEntity(entity: T) {
    if (!checkSearchEnabled()) return
    indexingTaskManager.scheduleTask(
      EmbeddingIndexingTask.AddDiskSynchronized(listOf(entity.id), listOf(entity.indexableRepresentation))
    )
  }

  fun deleteEntity(entity: T) {
    if (!checkSearchEnabled()) return
    indexingTaskManager.scheduleTask(
      EmbeddingIndexingTask.DeleteDiskSynchronized(listOf(entity.id))
    )
  }

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  @RequiresBackgroundThread
  fun generateEmbeddingsIfNecessary() = GLOBAL_INDEXING_LOCK.withLock {
    val indexableEntities = ProgressManager.getInstance().runProcess(this::getIndexableEntities, EmptyProgressIndicator())
    val storageSetupTask = DiskSynchronizedEmbeddingsStorageSetup(
      index, indexingTaskManager, indexableEntities, setupTaskIndicator, setupTitle)
    try {
      if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
        val indicator = BackgroundableProcessIndicator(null, setupTitle, null, "", true)
        ProgressManager.getInstance().runProcess({ storageSetupTask.run(indicator) }, indicator)
        ApplicationManager.getApplication().invokeLater { Disposer.dispose(indicator) }
      }
      else {
        val indicator = EmptyProgressIndicator()
        ProgressManager.getInstance().runProcess({ storageSetupTask.run(indicator) }, indicator)
      }
    } catch (e: ProcessCanceledException) {
      // do nothing
    }
    storageSetupTask.onFinish()
  }
}