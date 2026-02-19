package com.intellij.searchEverywhereMl.typos.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.regex.Pattern

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

  val deferredDictionary: Deferred<LanguageModelDictionary> = coroutineScope.async {
    val corpus = CorpusBuilder.getInstance()?.deferredCorpus?.await() ?: emptySet()
    computeLanguageModelDictionary(corpus)
  }

  // Accept any word that is between 3 and 45 characters long
  private val acceptableWordsPattern = Pattern.compile("^.{3,45}$")

  private fun computeLanguageModelDictionary(corpus: Set<List<String>>): LanguageModelDictionary {
    val cleanedSentences = corpus.map { sentence ->
      sentence.filter { word -> acceptableWordsPattern.matcher(word).matches() }
    }.filter { it.isNotEmpty() }

    val validWords = cleanedSentences.flatten()

    val dictionary = validWords.groupingBy { it }
      .eachCount()
      .let { SimpleLanguageModelDictionary(it) }

    return dictionary
  }

}
