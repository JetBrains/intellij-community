package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ml.embeddings.search.services.*
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

private class SemanticSearchInitializer : ProjectActivity {
  /**
   * Instantiate service for the first time with state loading if available.
   * Whether the state exists or not, we generate the missing embeddings:
   */
  override suspend fun execute(project: Project) {
    val searchEverywhereSemanticSettings = serviceAsync<SearchEverywhereSemanticSettings>()
    if (searchEverywhereSemanticSettings.enabledInActionsTab) {
      ActionEmbeddingsStorage.getInstance().prepareForSearch(project)
    }

    if (searchEverywhereSemanticSettings.enabledInClassesTab) {
      val classEmbeddingsStorage = project.serviceAsync<ClassEmbeddingsStorage>()
      classEmbeddingsStorage.registerIndexInMemoryManager()
      classEmbeddingsStorage.prepareForSearch()
    }

    if (searchEverywhereSemanticSettings.enabledInFilesTab) {
      val fileEmbeddingsStorage = project.serviceAsync<FileEmbeddingsStorage>()
      fileEmbeddingsStorage.registerIndexInMemoryManager()
      fileEmbeddingsStorage.prepareForSearch()
    }

    if (searchEverywhereSemanticSettings.enabledInSymbolsTab) {
      val embeddingStorage = project.serviceAsync<SymbolEmbeddingStorage>()
      embeddingStorage.registerIndexInMemoryManager()
      embeddingStorage.prepareForSearch()
    }

    VirtualFileManager.getInstance().addAsyncFileListener(
      SemanticSearchFileChangeListener.getInstance(project), IndexingLifecycleTracker.getInstance(project))
  }
}