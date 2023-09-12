package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.utils.LowMemoryNotificationManager
import com.intellij.searchEverywhereMl.semantics.utils.generateEmbedding
import com.intellij.searchEverywhereMl.semantics.utils.generateEmbeddings
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// Prohibits concurrent embedding indexing to reduce CPU usage
val GLOBAL_INDEXING_LOCK = ReentrantLock()

/**
 * Thread-safe service that linearizes all operations related to changes in embedding indices.
 * The order of operations is defined by the task scheduling order, calling thread may not wait until operations are finished.
 */
class EmbeddingIndexingTaskManager(private val index: DiskSynchronizedEmbeddingSearchIndex) {
  private val queue = LinkedList<EmbeddingIndexingTask>()
  private var active = false
  private val mutex = ReentrantLock()

  private val readyCondition = mutex.newCondition()

  fun scheduleTask(task: EmbeddingIndexingTask) = mutex.withLock {
    queue.add(task)
    if (!active) {
      active = true
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(
        object : Task.Backgroundable(null, PROCESSING_TASK_NAME) {
          override fun run(indicator: ProgressIndicator) = processingJob()
        },
        EmptyProgressIndicator()
      )
    }
  }

  fun waitUntilReady(forceWait: Boolean = false) = mutex.withLock {
    if (forceWait) {
      do readyCondition.await() while (active)
    }
    else {
      while (active) readyCondition.await()
    }
  }

  fun cancelIndexTasks() = mutex.withLock { queue.clear() }

  private fun processingJob() {
    while (true) {
      val task = mutex.withLock {
        if (queue.isEmpty()) return
        queue.pop()
      }
      if ((task is EmbeddingIndexingTask.Add || task is EmbeddingIndexingTask.AddDiskSynchronized)
          && !index.checkCanAddEntry()) {
        cancelIndexTasks()
        LowMemoryNotificationManager.getInstance().showNotification()
      }
      else {
        try {
          task.run(index)
        }
        catch (e: ProcessCanceledException) {
          cancelIndexTasks()
        }
      }
      mutex.withLock {
        if (queue.isEmpty()) {
          active = false
          readyCondition.signalAll()
          return
        }
      }
    }
  }

  companion object {
    private val PROCESSING_TASK_NAME = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.indexing.processing.label")
  }
}

sealed interface EmbeddingIndexingTask {
  fun run(index: DiskSynchronizedEmbeddingSearchIndex)

  class Add(
    private val ids: List<String>,
    private val texts: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embeddings = generateEmbeddings(texts) ?: return
      index.addEntries(ids zip embeddings)
      callback()
    }
  }

  class AddDiskSynchronized(
    private val ids: List<String>,
    private val texts: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embeddings = generateEmbeddings(texts) ?: return
      (ids zip embeddings).forEach { index.addEntry(it.first, it.second) }
      callback()
    }
  }

  class DeleteDiskSynchronized(
    private val ids: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      ids.forEach { index.deleteEntry(it) }
      callback()
    }
  }

  class RenameDiskSynchronized(
    private val oldId: String,
    private val newId: String,
    private val newIndexableRepresentation: String,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embedding = generateEmbedding(newIndexableRepresentation) ?: return
      index.updateEntry(oldId, newId, embedding)
      callback()
    }
  }
}