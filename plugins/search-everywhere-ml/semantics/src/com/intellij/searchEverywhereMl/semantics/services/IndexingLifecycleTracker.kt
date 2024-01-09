package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.listeners.SemanticIndexingFinishListener
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.PROJECT)
class IndexingLifecycleTracker: Disposable {
  private val mutex = ReentrantLock()
  private val condition = mutex.newCondition()
  private var readyIndexCount = 0

  private val totalIndexCount
    get() = SearchEverywhereSemanticSettings.getInstance().run {
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
    fun getInstance(project: Project): IndexingLifecycleTracker = project.service()
  }

  override fun dispose() {}
}

class IndexingLifecycleListener(private val project: Project): SemanticIndexingFinishListener {
  override fun finished(indexId: String?) = IndexingLifecycleTracker.getInstance(project).finished(indexId)
}