package com.intellij.searchEverywhereMl.typos

import ai.grazie.spell.suggestion.ranker.FrequencySuggestionRanker
import ai.grazie.spell.suggestion.ranker.JaroWinklerSuggestionRanker
import ai.grazie.spell.suggestion.ranker.LinearAggregatingSuggestionRanker
import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel
import com.intellij.spellchecker.SpellCheckerManager

internal class ActionsTabTypoFixSuggestionProvider(project: Project) {
  private val spellChecker = SpellCheckerManager.getInstance(project)

  // Use the backing field, so that we can just provide suggestion ranker if it has already been constructed,
  // otherwise we're going to try to create it using the language model - this may not be successful if the
  // language model is still being computed
  private var _suggestionRanker: SuggestionRanker? = null
  private val suggestionRanker: SuggestionRanker?
    get() {
      if (_suggestionRanker != null) return _suggestionRanker

      val languageModel = ActionsLanguageModel.getInstance() ?: return null
      if (!languageModel.isComputed) return null

      _suggestionRanker = LinearAggregatingSuggestionRanker(
        JaroWinklerSuggestionRanker() to 0.5,
        FrequencySuggestionRanker(languageModel) to 0.5,
      )
      return _suggestionRanker
    }

  fun suggestFixFor(query: String): SearchEverywhereSpellCheckResult = splitText(query)
    .map { token ->
      when (token) {
        is SearchEverywhereStringToken.Delimiter -> WordSpellCheckResult.NoCorrection(token.value)
        is SearchEverywhereStringToken.Word -> correctWord(token.value)
      }
    }
    .toQuerySpellCheckResult()

  private fun correctWord(word: String): WordSpellCheckResult = word.takeIf { spellChecker.hasProblem(it) }
    ?.let { misspelledWord ->
      val correctionSuggestions = spellChecker.getSuggestions(misspelledWord.lowercase()).toLinkedSet()

      suggestionRanker?.score(word.lowercase(), correctionSuggestions)
        ?.asSequence()
        ?.maxBy { it.value }
        ?.let { (correction, confidence) -> correction.capitalizeBasedOn(word) to confidence }
        ?.let { (word, confidence) -> WordSpellCheckResult.Correction(word, confidence) }
    } ?: WordSpellCheckResult.NoCorrection(word)

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

