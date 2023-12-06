package com.intellij.searchEverywhereMl.typos

import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.suggestion.ranker.FrequencySuggestionRanker
import ai.grazie.spell.suggestion.ranker.JaroWinklerSuggestionRanker
import ai.grazie.spell.suggestion.ranker.LinearAggregatingSuggestionRanker
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel
import com.intellij.searchEverywhereMl.typos.models.TypoSuggestionProvider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

internal class ActionsTabTypoFixSuggestionProvider {
  private val deferredSuggestionProvider: Deferred<TypoSuggestionProvider>? =
    ActionsLanguageModel.getInstance()
      ?.let { model ->
        model.coroutineScope.async {
          val dictionary = model.deferredDictionary.await()
          val ranker = getSuggestionRanker(dictionary)
          TypoSuggestionProvider(dictionary, ranker)
        }
      }

  private fun getSuggestionRanker(dictionary: FrequencyMetadata) = LinearAggregatingSuggestionRanker(
    JaroWinklerSuggestionRanker() to 0.5,
    FrequencySuggestionRanker(dictionary) to 0.5,
  )

  fun suggestFixFor(query: String): SearchEverywhereSpellCheckResult {
    if (isActionAbbreviation(query)) return SearchEverywhereSpellCheckResult.NoCorrection

    return splitText(query)
      .map { token ->
        when (token) {
          is SearchEverywhereStringToken.Delimiter -> WordSpellCheckResult.NoCorrection(token.value)
          is SearchEverywhereStringToken.Word -> correctWord(token.value)
        }
      }
      .toQuerySpellCheckResult()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun correctWord(word: String): WordSpellCheckResult {
    if (deferredSuggestionProvider == null || !deferredSuggestionProvider.isCompleted) return WordSpellCheckResult.NoCorrection(word)

    val suggestionProvider = deferredSuggestionProvider.getCompleted()
    val lowercaseWord = word.lowercase()
    if (!suggestionProvider.isMisspelled(word.lowercase())) return WordSpellCheckResult.NoCorrection(word)

    return suggestionProvider.getSuggestions(lowercaseWord, 5)
             .maxByOrNull { it.score }
             ?.let { (correction, confidence) -> correction.toString().capitalizeBasedOn(word) to confidence }
             ?.let { (word, confidence) -> WordSpellCheckResult.Correction(word, confidence) }
           ?: WordSpellCheckResult.NoCorrection(word)
  }

  private fun isActionAbbreviation(query: String): Boolean {
    return AbbreviationManager.getInstance().findActions(query).isNotEmpty()
  }

  private sealed class WordSpellCheckResult(val word: String) {
    class Correction(suggestion: String, val confidence: Double) : WordSpellCheckResult(suggestion)
    class NoCorrection(originalWord: String) : WordSpellCheckResult(originalWord)
  }

  private fun Sequence<WordSpellCheckResult>.toQuerySpellCheckResult(): SearchEverywhereSpellCheckResult {
    if (all { it is WordSpellCheckResult.NoCorrection }) return SearchEverywhereSpellCheckResult.NoCorrection

    val correctedQuery = joinToString("") { it.word }
    val confidence = filterIsInstance<WordSpellCheckResult.Correction>().map { it.confidence }.average()
    return SearchEverywhereSpellCheckResult.Correction(correctedQuery, confidence)
  }

  private fun String.capitalizeBasedOn(other: String): String {
    if (other.all { it.isLowerCase() }) return this.lowercase()
    if (other.all { it.isUpperCase() }) return this.uppercase()
    if (other.first().isUpperCase()) return this.replaceFirstChar { it.uppercase() }
    else return this
  }
}
