package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.helpers.computeWithSpan
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.listeners.SemanticIndexingFinishListener
import com.intellij.searchEverywhereMl.semantics.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.searchEverywhereMl.semantics.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.withLock

abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project) {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex
  abstract val indexingTaskManager: EmbeddingIndexingTaskManager

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  abstract val scanningTitle: String
  abstract val setupTitle: String
  abstract val spanIndexName: String

  abstract val indexMemoryWeight: Int
  open val indexStrongLimit: Int? = null

  abstract fun checkSearchEnabled(): Boolean

  abstract fun getIndexableEntities(): List<T>

  fun prepareForSearch() {
    // Delay embedding indexing until standard indexing is finished
    DumbService.getInstance(project).runWhenSmart {
      ApplicationManager.getApplication().executeOnPooledThread {
        LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
        EmbeddingIndexMemoryManager.getInstance().registerIndex(index, indexMemoryWeight, indexStrongLimit)
        index.loadFromDisk()
        logger.debug { "Loaded embedding index from disk, size: ${index.size}, root: ${index.root}" }
        generateEmbeddingsIfNecessary()
        project.messageBus.syncPublisher(SemanticIndexingFinishListener.FINISHED).finished()
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
      EmbeddingIndexingTask.AddDiskSynchronized(listOf(entity.id.intern()), listOf(entity.indexableRepresentation.intern()))
    )
  }

  fun deleteEntity(entity: T) {
    if (!checkSearchEnabled()) return
    indexingTaskManager.scheduleTask(
      EmbeddingIndexingTask.DeleteDiskSynchronized(listOf(entity.id.intern()))
    )
  }

  @RequiresBackgroundThread
  fun generateEmbeddingsIfNecessary() = GLOBAL_INDEXING_LOCK.withLock {
    logger.debug { "Started indexing for ${this.javaClass.simpleName}" }
    val scanningIndicator = BackgroundableProcessIndicator(project, scanningTitle, null, "", true)
    val indexableEntities = computeWithSpan(SEMANTIC_SEARCH_TRACER, spanIndexName + "Scanning") {
      try {
        ProgressManager.getInstance().runProcess(this::getIndexableEntities, scanningIndicator)
      }
      catch (_: ProcessCanceledException) {
        return
      }
    }
    ApplicationManager.getApplication().invokeLater { Disposer.dispose(scanningIndicator) }
    logger.debug { "Found ${indexableEntities.size} indexable entities for ${this.javaClass.simpleName}" }

    val storageSetupTask = DiskSynchronizedEmbeddingsStorageSetup(
      index, indexingTaskManager, indexableEntities, setupTaskIndicator, setupTitle)
    runWithSpan(SEMANTIC_SEARCH_TRACER, spanIndexName + "Indexing") {
      try {
        if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
          val indicator = BackgroundableProcessIndicator(project, setupTitle, null, "", true)
          ProgressManager.getInstance().runProcess({ storageSetupTask.run(indicator) }, indicator)
          ApplicationManager.getApplication().invokeLater { Disposer.dispose(indicator) }
        }
        else {
          val indicator = EmptyProgressIndicator()
          ProgressManager.getInstance().runProcess({ storageSetupTask.run(indicator) }, indicator)
        }
      }
      catch (_: ProcessCanceledException) {
        // do nothing, finish with what we indexed
      }
      storageSetupTask.onFinish()
      logger.debug { "Finished indexing for ${this.javaClass.simpleName}" }
    }
  }

  companion object {
    private val logger by lazy { Logger.getInstance(DiskSynchronizedEmbeddingsStorage::class.java) }
  }
}