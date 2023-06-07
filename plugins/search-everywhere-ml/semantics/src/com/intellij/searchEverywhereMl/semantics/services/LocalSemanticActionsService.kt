package com.intellij.searchEverywhereMl.semantics.services

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
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
import com.intellij.openapi.util.NlsSafe
import java.util.concurrent.atomic.AtomicBoolean
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
class LocalSemanticActionsService : PersistentStateComponent<LocalSemanticActionsService.State> {
  data class State(var actionIdToEmbedding: MutableMap<String, FloatTextEmbedding>) {
    constructor() : this(mutableMapOf())
  }

  var myState = State()
  private val stateLock = ReentrantReadWriteLock()
  private val isGeneratingEmbeddings = AtomicBoolean(false)

  override fun getState(): State {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
    // If the storage file is changed externally, we make it consistent again:
    isGeneratingEmbeddings.compareAndSet(true, false)
    tryGenerateEmbeddings()
  }

  @Suppress("unused")  // Registered in the plugin's XML file
  private class SearchServiceInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
      // Instantiate service for the first time with state loading if available.
      // Whether the state exists or not, we generate the missing embeddings:
      service<LocalSemanticActionsService>().tryGenerateEmbeddings()
    }
  }

  /* Thread-safe job for updating embeddings. Only the first call will have an effect. */
  fun tryGenerateEmbeddings() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val project = ProjectManager.getInstance().openProjects[0]
      ProgressManager.getInstance().run(EmbeddingStorageSetupTask(project))
    }
  }

  private fun computeEmbedding(text: String): FloatTextEmbedding {
    return runBlockingCancellable {
      LocalEmbeddingServiceProvider.getService().embed(listOf(text))
    }.single()
  }

  fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText> {
    val computeTask = { computeEmbedding(text) }
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

  private fun shouldGenerateEmbeddings(): Boolean {
    val indexableActionsCount = getIndexableActionsCount()
    return stateLock.read {
      // Do not try to instantiate the local embedding model if all embeddings already present
      myState.actionIdToEmbedding.size != indexableActionsCount
    }
  }

  private inner class EmbeddingStorageSetupTask(project: Project) : Task.Backgroundable(project, SETUP_TITLE) {
    override fun run(indicator: ProgressIndicator) {
      if (!isGeneratingEmbeddings.compareAndSet(false, true) || !shouldGenerateEmbeddings()) {
        return
      }

      indicator.text = SETUP_TITLE
      // There is an inspection that prohibits the usage of `runBlocking` to run coroutines
      // and suggests to replace it with `runBlockingCancellable`. `runBlockingCancellable` requires
      // the presence of `ProgressIndicator` in the calling thread; that's another reason we use indicator here
      val embeddingService = runBlockingCancellable {
        LocalEmbeddingServiceProvider.getService()
      }

      var indexedActionsCount = 0
      val indexableActionsCount = getIndexableActionsCount()

      val actionManager = ActionManager.getInstance() as ActionManagerImpl
      actionManager.actionIds
        .asSequence()
        .map { actionId ->
          actionId to actionManager.getAction(actionId)
        }.filter {
          val action = it.second
          action != null && !(action is ActionGroup && !action.isSearchable) && action.templateText != null
        }
        .filter {
          // Do not calculate the embedding if already present in state
          stateLock.read {
            it.first !in myState.actionIdToEmbedding
          }
        }.chunked(BATCH_SIZE) {
          it
        }.forEach {
          ProgressManager.checkCanceled()
          val actionIds = it.map { pair -> pair.first }
          val texts = it.map { pair -> pair.second!!.templateText!! }
          val embeddings = runBlockingCancellable {
            embeddingService.embed(texts)
          }.map { embedding ->
            embedding.normalized()
          }
          stateLock.write {
            myState.actionIdToEmbedding.putAll(actionIds zip embeddings)
          }
          indexedActionsCount += embeddings.size
          indicator.fraction = indexedActionsCount.toDouble() / indexableActionsCount
        }

      indicator.cancel()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(LocalSemanticActionsService::class.java)

    @NlsSafe
    private const val SETUP_TITLE = "Generating actions embeddings..."
    private const val BATCH_SIZE = 16

    private fun getIndexableActionsCount(): Int {
      val actionManager = ActionManager.getInstance() as ActionManagerImpl
      return actionManager.actionIds
        .map {
          actionManager.getAction(it)
        }.filter {
          it != null && !(it is ActionGroup && !it.isSearchable) && it.templateText != null
        }.size
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