package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.platform.ml.embeddings.utils.normalized
import com.intellij.platform.util.progress.durationStep
import com.intellij.searchEverywhereMl.semantics.indices.EmbeddingSearchIndex
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class ActionEmbeddingsStorageSetup(
  private val index: EmbeddingSearchIndex,
  private val indexSetupJob: AtomicReference<Job>
) {
  private var shouldSaveToDisk = true

  suspend fun run() = coroutineScope {
    val indexableActionIds = ActionEmbeddingsStorage.getIndexableActionIds()
    if (checkEmbeddingsReady(indexableActionIds)) {
      shouldSaveToDisk = false
      return@coroutineScope
    }

    val embeddingService = LocalEmbeddingServiceProvider.getInstance().getService() ?: return@coroutineScope
    // Cancel the previous embeddings calculation task if it's not finished
    indexSetupJob.getAndSet(launch {
      val indexedActionsCount = index.size
      val totalIndexableActionsCount = indexableActionIds.size

      val actionManager = ActionManager.getInstance() as ActionManagerImpl
      indexableActionIds
        .asSequence()
        .filter { it !in index }
        .map { it to actionManager.getActionOrStub(it) }
        .chunked(BATCH_SIZE)
        .forEach { batch ->
          val actionIds = batch.map { (id, _) -> id }
          val texts = batch.map { (_, action) -> action!!.templateText!! }

          durationStep(texts.size.toDouble() / (totalIndexableActionsCount - indexedActionsCount), null) {
            val embeddings = embeddingService.embed(texts).map { it.normalized() }
            index.addEntries(actionIds zip embeddings)
          }
        }
    })?.cancel()
  }

  fun onFinish(cs: CoroutineScope) {
    indexSetupJob.set(null)
    if (shouldSaveToDisk) {
      cs.launch(Dispatchers.IO) {
        index.saveToDisk()
      }
    }
  }

  private fun checkEmbeddingsReady(indexableActionIds: Set<String>): Boolean {
    index.filterIdsTo(indexableActionIds.associateWith { 1 })
    return index.checkAllIdsPresent(indexableActionIds)
  }

  companion object {
    private const val BATCH_SIZE = 1
  }
}