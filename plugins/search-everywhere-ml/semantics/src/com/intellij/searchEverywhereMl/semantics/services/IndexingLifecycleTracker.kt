package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.listeners.SemanticIndexingFinishListener
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.PROJECT)
class IndexingLifecycleTracker {
  private val mutex = ReentrantLock()
  private val condition = mutex.newCondition()
  private var readyIndexCount = 0

  private val totalIndexCount
    get() = SemanticSearchSettings.getInstance().run {
      listOf(enabledInFilesTab, enabledInClassesTab, enabledInSymbolsTab).filter { it }.size
    }

  fun finished(indexId: String?) = mutex.withLock {
    readyIndexCount++
    if (readyIndexCount == totalIndexCount) {
      condition.signalAll()
    }
  }

  fun waitIndicesReady() = mutex.withLock {
    while (readyIndexCount != totalIndexCount) condition.await()
  }

  companion object {
    fun getInstance(project: Project) = project.service<IndexingLifecycleTracker>()
  }
}

class IndexingLifecycleListener(private val project: Project): SemanticIndexingFinishListener {
  override fun finished(indexId: String?) = IndexingLifecycleTracker.getInstance(project).finished(indexId)
}