package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments.SemanticSearchFeature
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

class SemanticSearchInitializer : ProjectActivity {
  /**
   * Instantiate service for the first time with state loading if available.
   * Whether the state exists or not, we generate the missing embeddings:
   */
  override suspend fun execute(project: Project) {
    if (SemanticSearchSettings.getInstance().enabledInActionsTab) {
      ActionEmbeddingsStorage.getInstance().prepareForSearch(project)
    }
    else if ((ApplicationManager.getApplication().isInternal
              || (ApplicationManager.getApplication().isEAP &&
                  SearchEverywhereSemanticExperiments.getInstance().getSemanticFeatureForTab(
                    ActionSearchEverywhereContributor::class.java.simpleName) == SemanticSearchFeature.ENABLED))
             && !SemanticSearchSettings.getInstance().manuallyDisabledInActionsTab
    ) {
      // Manually enable search in the corresponding experiment groups
      SemanticSearchSettings.getInstance().enabledInActionsTab = true
    }

    if (SemanticSearchSettings.getInstance().enabledInClassesTab) {
      ClassEmbeddingsStorage.getInstance(project).registerIndexInMemoryManager()
      ClassEmbeddingsStorage.getInstance(project).prepareForSearch()
    }

    if (SemanticSearchSettings.getInstance().enabledInFilesTab) {
      FileEmbeddingsStorage.getInstance(project).registerIndexInMemoryManager()
      FileEmbeddingsStorage.getInstance(project).prepareForSearch()
    }

    if (SemanticSearchSettings.getInstance().enabledInSymbolsTab) {
      SymbolEmbeddingStorage.getInstance(project).registerIndexInMemoryManager()
      SymbolEmbeddingStorage.getInstance(project).prepareForSearch()
    }

    if (SemanticSearchSettings.getInstance().enabledInClassesTab
        || SemanticSearchSettings.getInstance().enabledInSymbolsTab) {
      VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileContentListener.getInstance(project),
                                                            IndexingLifecycleTracker.getInstance(project))
    }

    if (SemanticSearchSettings.getInstance().enabledInFilesTab) {
      VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileNameListener.getInstance(project),
                                                            IndexingLifecycleTracker.getInstance(project))
    }
  }
}