package com.intellij.searchEverywhereMl.semantics.services

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettingsManager
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.sqrt

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk at the IDE close event.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event.
 */
@Service(Service.Level.APP)
@State(
  name = "ActionEmbeddingsStorage",
  storages = [Storage(value = "action-embeddings.xml", roamingType = RoamingType.DISABLED)],
  reportStatistic = false
)
class ActionEmbeddingsStorage : PersistentStateComponent<ActionEmbeddingsStorage.State> {
  data class State(var actionIdToEmbedding: MutableMap<String, FloatTextEmbedding>) {
    constructor() : this(mutableMapOf())
  }

  private var myState = State()
  private val stateLock = ReentrantReadWriteLock()

  private val setupTaskIndicator = AtomicReference<ProgressIndicator>(null)

  override fun getState(): State {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
  }

  /* Thread-safe job for updating embeddings. Only the first call will have an effect. */
  fun tryGenerateEmbeddings() {
    if (checkSearchEnabled()) {
      ApplicationManager.getApplication().executeOnPooledThread {
        val project = ProjectManager.getInstance().openProjects[0]
        val task = EmbeddingStorageSetupTask(project)
        ProgressManager.getInstance().run(task)
      }
    }
  }

  fun tryStopGeneratingEmbeddings() {
    setupTaskIndicator.getAndSet(null)?.cancel()
  }

  private fun computeEmbedding(text: String): FloatTextEmbedding {
    return runBlockingCancellable {
      service<LocalEmbeddingServiceProvider>().getService().embed(listOf(text))
    }.single()
  }

  fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText> {
    if (!checkSearchEnabled()) return emptyList()

    val computeTask = { computeEmbedding(text).normalized() }
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

      // Cancel the previous embeddings calculation task if it's not finished
      setupTaskIndicator.getAndSet(indicator)?.cancel()

      indicator.text = SETUP_TITLE
      // There is an inspection that prohibits the usage of `runBlocking` to run coroutines
      // and suggests replacing it with `runBlockingCancellable` which requires
      // the presence of `ProgressIndicator` in the calling thread; that's another reason we use the indicator here
      val embeddingService = runBlockingCancellable { service<LocalEmbeddingServiceProvider>().getService() }

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
  }

  companion object {
    private val LOG = Logger.getInstance(ActionEmbeddingsStorage::class.java)

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
    service<ActionEmbeddingsStorage>().tryGenerateEmbeddings()
  }
}