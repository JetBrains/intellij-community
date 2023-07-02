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
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.LocalEmbeddingIndexManager
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettingsManager
import com.intellij.util.containers.CollectionFactory
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.sqrt

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage {
  data class State(var actionIdToEmbedding: MutableMap<String, FloatTextEmbedding>) {
    constructor() : this(CollectionFactory.createSmallMemoryFootprintMap<String, FloatTextEmbedding>())
  }

  private var myState = State()
  private val stateLock = ReentrantReadWriteLock()

  private val root = File(PathManager.getSystemPath()).resolve("semantic-search").also { Files.createDirectories(it.toPath()) }
  private val embeddingIndexManager = LocalEmbeddingIndexManager(root.resolve("actions").toPath())

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  fun prepareForSearch() {
    ApplicationManager.getApplication().executeOnPooledThread {
      service<LocalArtifactsManager>().tryPrepareArtifacts()
      myState = stateLock.write { State(embeddingIndexManager.loadIndex()) }
      service<ActionEmbeddingsStorage>().tryGenerateEmbeddings()
    }
  }

  fun tryStopGeneratingEmbeddings() {
    setupTaskIndicator.getAndSet(null)?.cancel()
  }

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  private fun tryGenerateEmbeddings() {
    val project = ProjectManager.getInstance().openProjects[0]
    ProgressManager.getInstance().run(EmbeddingStorageSetupTask(project))
  }

  fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText> {
    if (!checkSearchEnabled()) return emptyList()
    val localEmbeddingService = runBlockingCancellable { service<LocalEmbeddingServiceProvider>().getService() } ?: return emptyList()

    val computeTask = { runBlockingCancellable { localEmbeddingService.embed(listOf(text)) }.single().normalized() }
    val embedding = ProgressManager.getInstance().runProcess(computeTask, EmptyProgressIndicator())

    return stateLock.read {
      myState.actionIdToEmbedding.mapValues {
        embedding.times(it.value)
      }.filter {
        if (similarityThreshold != null) it.value > similarityThreshold else true
      }.toList().sortedByDescending {
        it.second
      }.take(topK).map {
        ScoredText(it.first, it.second.toDouble())
      }
    }
  }

  private fun hasUnreadyEmbeddings(): Boolean {
    val totalIndexableActionsCount = getTotalIndexableActionsCount()
    return stateLock.read {
      // Do not try to instantiate the local embedding model if all embeddings already present
      myState.actionIdToEmbedding.size != totalIndexableActionsCount
    }
  }

  private inner class EmbeddingStorageSetupTask(project: Project) : Task.Backgroundable(project, SETUP_TITLE) {
    override fun run(indicator: ProgressIndicator) {
      if (!hasUnreadyEmbeddings()) {
        return
      }

      // There is an inspection that prohibits the usage of `runBlocking` to run coroutines
      // and suggests replacing it with `runBlockingCancellable` which requires
      // the presence of `ProgressIndicator` in the calling thread; that's another reason we use the indicator here
      val embeddingService = runBlockingCancellable { service<LocalEmbeddingServiceProvider>().getService() } ?: return

      // Cancel the previous embeddings calculation task if it's not finished
      setupTaskIndicator.getAndSet(indicator)?.cancel()

      indicator.text = SETUP_TITLE

      var indexedActionsCount = stateLock.read { myState.actionIdToEmbedding.size }
      val totalIndexableActionsCount = getTotalIndexableActionsCount()

      val actionManager = ActionManager.getInstance() as ActionManagerImpl
      actionManager.actionIds
        .asSequence()
        .map { it to actionManager.getActionOrStub(it) }
        .filter { shouldIndexAction(it.second) }
        .filter {
          // Do not calculate the embedding if already present in state
          stateLock.read { it.first !in myState.actionIdToEmbedding }
        }
        .chunked(BATCH_SIZE)
        .forEach {
          ProgressManager.checkCanceled()
          val actionIds = it.map { pair -> pair.first }
          val texts = it.map { pair -> pair.second!!.templateText!! }
          val embeddings = runBlockingCancellable { embeddingService.embed(texts) }
            .map { embedding -> embedding.normalized() }
          stateLock.write { myState.actionIdToEmbedding.putAll(actionIds zip embeddings) }
          indexedActionsCount += embeddings.size
          indicator.fraction = indexedActionsCount.toDouble() / totalIndexableActionsCount
        }

      // If the indicator is already changed, then the current task is already canceled
      if (setupTaskIndicator.compareAndSet(indicator, null)) {
        indicator.cancel()
      }
    }

    override fun onCancel() {
      embeddingIndexManager.saveIndex(stateLock.read { myState.actionIdToEmbedding })
    }
  }

  companion object {
    private val SETUP_TITLE = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.actions.generation.label")
    private const val BATCH_SIZE = 16

    private fun checkSearchEnabled(): Boolean {
      return service<SemanticSearchSettingsManager>().getIsEnabledInActionsTab()
    }

    private fun shouldIndexAction(action: AnAction?): Boolean {
      return action != null && !(action is ActionGroup && !action.isSearchable) && action.templateText != null
    }

    private fun getTotalIndexableActionsCount(): Int {
      return (ActionManager.getInstance() as ActionManagerImpl).actionsOrStubs().filter { shouldIndexAction(it) }.count()
    }
  }
}

data class ScoredText(
  val text: String,
  val similarity: Double
)

fun FloatTextEmbedding.normalized(): FloatTextEmbedding {
  val norm = sqrt(this * this)
  return FloatTextEmbedding(this.values.map { it / norm }.toFloatArray())
}

@Suppress("unused")  // Registered in the plugin's XML file
class ActionSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Instantiate service for the first time with state loading if available.
    // Whether the state exists or not, we generate the missing embeddings:
    if (service<SemanticSearchSettingsManager>().getIsEnabledInActionsTab()) {
      service<ActionEmbeddingsStorage>().prepareForSearch()
    }
  }
}