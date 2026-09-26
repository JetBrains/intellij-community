package com.intellij.searchEverywhereMl.typos.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

@Service(Service.Level.APP)
internal class ActionsLanguageModel(val coroutineScope: CoroutineScope) {
  companion object {
    /**
     * Returns null if typo-tolerant search is disabled in the Advanced Settings
     */
    fun getInstance(): ActionsLanguageModel? {
      if (!isTypoFixingEnabled) {
        return null
      }
      return service<ActionsLanguageModel>()
    }
  }

  val deferredSharedIndex: Deferred<ActionsTypoSharedIndex> = coroutineScope.async {
    val corpus = CorpusBuilder.getInstance()?.buildCorpus() ?: emptySet()
    ActionsTypoSharedIndex.create(corpus)
  }

  val deferredDictionary: Deferred<LanguageModelDictionary> = coroutineScope.async {
    deferredSharedIndex.await().asDictionary()
  }

  private val deferredPrefixMatcher: Deferred<ActionsTypoPrefixMatcher> = coroutineScope.async(start = CoroutineStart.LAZY) {
    deferredSharedIndex.await().createPrefixMatcher()
  }

  private val deferredNGramModels: Deferred<ActionsTypoNGramModels> = coroutineScope.async(start = CoroutineStart.LAZY) {
    ActionsTypoNGramModels.create(deferredSharedIndex.await())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getReadyPrefixMatcherOrNull(): ActionsTypoPrefixMatcher? {
    return deferredPrefixMatcher
      .takeIf { it.isCompleted }
      ?.getCompleted()
  }

  fun ensurePrefixMatcherBuilding() {
    deferredPrefixMatcher.start()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getReadyNGramModelsOrNull(): ActionsTypoNGramModels? {
    return deferredNGramModels
      .takeIf { it.isCompleted }
      ?.getCompleted()
  }

  fun ensureNGramModelsBuilding() {
    deferredNGramModels.start()
  }
}
