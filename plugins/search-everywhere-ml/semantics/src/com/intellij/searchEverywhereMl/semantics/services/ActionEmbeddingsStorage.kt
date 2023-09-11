package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments.SemanticSearchFeature
import com.intellij.searchEverywhereMl.semantics.indices.InMemoryEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.searchEverywhereMl.semantics.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage {
  val index = InMemoryEmbeddingSearchIndex(
    File(PathManager.getSystemPath())
      .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
      .resolve(LocalArtifactsManager.getInstance().getModelVersion())
      .resolve(INDEX_DIR).toPath()
  )

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  fun prepareForSearch(project: Project) {
    DumbService.getInstance(project).runWhenSmart {
      ApplicationManager.getApplication().executeOnPooledThread {
        LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
        index.loadFromDisk()
        generateEmbeddingsIfNecessary()
      }
    }
  }

  fun tryStopGeneratingEmbeddings() = setupTaskIndicator.getAndSet(null)?.cancel()

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  @RequiresBackgroundThread
  fun generateEmbeddingsIfNecessary() {
    val backgroundable = ActionEmbeddingsStorageSetup(index, setupTaskIndicator)
    if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
      ProgressManager.getInstance().run(backgroundable)
    }
    else {
      val indicator = EmptyProgressIndicator()
      ProgressManager.getInstance().runProcess({ backgroundable.run(indicator) }, indicator)
      backgroundable.onCancel()
    }
  }

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

  companion object {
    private const val INDEX_DIR = "actions"

    fun getInstance() = service<ActionEmbeddingsStorage>()

    private fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInActionsTab

    private fun shouldIndexAction(action: AnAction?): Boolean {
      return action != null && !(action is ActionGroup && !action.isSearchable) && action.templateText != null
    }

    internal fun getIndexableActionIds(): Set<String> {
      val actionManager = (ActionManager.getInstance() as ActionManagerImpl)
      return actionManager.actionIds.filter { shouldIndexAction(actionManager.getActionOrStub(it)) }.toSet()
    }
  }
}

@Suppress("unused")  // Registered in the plugin's XML file
class ActionSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Instantiate service for the first time with state loading if available.
    // Whether the state exists or not, we generate the missing embeddings:
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
  }
}