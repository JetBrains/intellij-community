package com.intellij.searchEverywhereMl.semantics.services

import ai.grazie.emb.FloatTextEmbedding
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
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.searchEverywhereMl.semantics.indices.InMemoryEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage {
  private val root = File(PathManager.getSystemPath()).resolve(SEMANTIC_SEARCH_RESOURCES_DIR).also { Files.createDirectories(it.toPath()) }

  private val index = InMemoryEmbeddingSearchIndex(root.resolve(INDEX_DIR).toPath())

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  fun prepareForSearch() {
    ApplicationManager.getApplication().executeOnPooledThread {
      // LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
      index.loadFromDisk()
      generateEmbeddingsIfNecessary()
    }
  }

  fun tryStopGeneratingEmbeddings() = setupTaskIndicator.getAndSet(null)?.cancel()

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  private fun generateEmbeddingsIfNecessary() {
    val project = ProjectManager.getInstance().openProjects[0]
    ProgressManager.getInstance().run(ActionEmbeddingStorageSetup(project, index, setupTaskIndicator))
  }

  fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText> {
    if (!checkSearchEnabled()) return emptyList()
    val localEmbeddingService = runBlockingCancellable { LocalEmbeddingServiceProvider.getInstance().getService() } ?: return emptyList()
    val computeTask = { runBlockingCancellable { localEmbeddingService.embed(listOf(text)) }.single().normalized() }
    val embedding = ProgressManager.getInstance().runProcess(computeTask, EmptyProgressIndicator())
    return index.findClosest(embedding, topK, similarityThreshold)
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

data class ScoredText(val text: String, val similarity: Double)

fun FloatTextEmbedding.normalized(): FloatTextEmbedding {
  val norm = sqrt(this * this)
  return FloatTextEmbedding(this.values.map { it / norm }.toFloatArray())
}

@Suppress("unused")  // Registered in the plugin's XML file
class ActionSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Instantiate service for the first time with state loading if available.
    // Whether the state exists or not, we generate the missing embeddings:
    if (SemanticSearchSettings.getInstance().enabledInActionsTab) {
      ActionEmbeddingsStorage.getInstance().prepareForSearch()
    }
  }
}