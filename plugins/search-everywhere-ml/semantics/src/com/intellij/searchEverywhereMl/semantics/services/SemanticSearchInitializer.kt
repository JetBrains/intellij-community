package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ml.embeddings.search.services.*
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments.SemanticSearchFeature
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings

private class SemanticSearchInitializer : ProjectActivity {
  /**
   * Instantiate service for the first time with state loading if available.
   * Whether the state exists or not, we generate the missing embeddings:
   */
  override suspend fun execute(project: Project) {
    val semanticSearchSettings = serviceAsync<SemanticSearchSettings>()
    if (semanticSearchSettings.enabledInActionsTab) {
      ActionEmbeddingsStorage.getInstance().prepareForSearch(project)
    }
    else if ((ApplicationManager.getApplication().isInternal ||
              (ApplicationManager.getApplication().isEAP &&
               serviceAsync<SearchEverywhereSemanticExperiments>()
                 .getSemanticFeatureForTab(ActionSearchEverywhereContributor::class.java.simpleName) == SemanticSearchFeature.ENABLED)) &&
             !semanticSearchSettings.manuallyDisabledInActionsTab) {
      // Manually enable search in the corresponding experiment groups
      semanticSearchSettings.enabledInActionsTab = true
    }

    if (semanticSearchSettings.enabledInClassesTab) {
      val classEmbeddingsStorage = project.serviceAsync<ClassEmbeddingsStorage>()
      classEmbeddingsStorage.registerIndexInMemoryManager()
      classEmbeddingsStorage.prepareForSearch()
    }

    if (semanticSearchSettings.enabledInFilesTab) {
      val fileEmbeddingsStorage = project.serviceAsync<FileEmbeddingsStorage>()
      fileEmbeddingsStorage.registerIndexInMemoryManager()
      fileEmbeddingsStorage.prepareForSearch()
    }

    if (semanticSearchSettings.enabledInSymbolsTab) {
      val embeddingStorage = project.serviceAsync<SymbolEmbeddingStorage>()
      embeddingStorage.registerIndexInMemoryManager()
      embeddingStorage.prepareForSearch()
    }

    if (semanticSearchSettings.enabledInClassesTab || semanticSearchSettings.enabledInSymbolsTab) {
      VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileContentListener.getInstance(project),
                                                            IndexingLifecycleTracker.getInstance(project))
    }

    if (semanticSearchSettings.enabledInFilesTab) {
      VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileNameListener.getInstance(project),
                                                            IndexingLifecycleTracker.getInstance(project))
    }
  }
}