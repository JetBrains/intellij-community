package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.InMemoryEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.semantics.utils.ScoredText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage(private val cs: CoroutineScope) : AbstractEmbeddingsStorage() {
  val index = InMemoryEmbeddingSearchIndex(
    File(PathManager.getSystemPath())
      .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
      .resolve(LocalArtifactsManager.getInstance().getModelVersion())
      .resolve(INDEX_DIR).toPath()
  )

  private val indexSetupJob = AtomicReference<Job>(null)

  private val setupTitle
    get() = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.actions.generation.label")

  fun prepareForSearch(project: Project) = cs.launch {
    project.waitForSmartMode() // project may become dumb again, but we don't interfere initial indexing
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
    index.loadFromDisk()
    generateEmbeddingsIfNecessary(project)
  }

  fun tryStopGeneratingEmbeddings() = indexSetupJob.getAndSet(null)?.cancel()

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  @RequiresBackgroundThread
  suspend fun generateEmbeddingsIfNecessary(project: Project) {
    val backgroundable = ActionEmbeddingsStorageSetup(index, indexSetupJob)
    try {
      if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
        withBackgroundProgress(project, setupTitle) {
          backgroundable.run()
        }
      }
      else {
        backgroundable.run()
      }
    }
    catch (e: CancellationException) {
      logger.debug("Actions embedding indexing was cancelled")
      throw e
    }
    finally {
      backgroundable.onFinish(cs)
    }
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighboursIfEnabled(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    if (!checkSearchEnabled()) return emptyList()
    return searchNeighbours(text, topK, similarityThreshold)
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    val embedding = generateEmbedding(text) ?: return emptyList()
    return index.findClosest(embedding, topK, similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    if (!checkSearchEnabled()) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  companion object {
    private const val INDEX_DIR = "actions"

    private val logger = Logger.getInstance(ActionEmbeddingsStorage::class.java)

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