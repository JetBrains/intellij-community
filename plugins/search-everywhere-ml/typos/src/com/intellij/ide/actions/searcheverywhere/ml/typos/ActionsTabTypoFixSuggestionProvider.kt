package com.intellij.ide.actions.searcheverywhere.ml.typos

import ai.grazie.spell.suggestion.ranker.FrequencySuggestionRanker
import ai.grazie.spell.suggestion.ranker.JaroWinklerSuggestionRanker
import ai.grazie.spell.suggestion.ranker.LinearAggregatingSuggestionRanker
import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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

      val languageModel = service<ActionsLanguageModel>()
      if (!languageModel.isComputed) return null

      _suggestionRanker = LinearAggregatingSuggestionRanker(
        JaroWinklerSuggestionRanker() to 0.5,
        FrequencySuggestionRanker(languageModel) to 0.5,
      )
      return _suggestionRanker
    }

  fun suggestFixFor(query: String): SearchEverywhereSpellCheckResult = query.split(" ")
    .filter { it.isNotBlank() }
    .map(::correctWord)
    .toQuerySpellCheckResult()

  private fun correctWord(word: String): WordSpellCheckResult = word.takeIf { spellChecker.hasProblem(it) }
    ?.let { misspelledWord ->
      val correctionSuggestions = spellChecker.getSuggestions(misspelledWord).toLinkedSet()

      suggestionRanker?.score(word, correctionSuggestions)
        ?.asSequence()
        ?.maxBy { it.value }
        ?.let { (word, confidence) -> WordSpellCheckResult.Correction(word, confidence) }
    } ?: WordSpellCheckResult.NoCorrection(word)

  private sealed class WordSpellCheckResult(val word: String) {
    class Correction(suggestion: String, val confidence: Double) : WordSpellCheckResult(suggestion)
    class NoCorrection(originalWord: String) : WordSpellCheckResult(originalWord)
  }

  private fun Collection<WordSpellCheckResult>.toQuerySpellCheckResult(): SearchEverywhereSpellCheckResult {
    if (all { it is WordSpellCheckResult.NoCorrection }) return SearchEverywhereSpellCheckResult.NoCorrection

    val correctedQuery = joinToString(" ") { it.word }
    val confidence = filterIsInstance<WordSpellCheckResult.Correction>().map { it.confidence }.average()
    return SearchEverywhereSpellCheckResult.Correction(correctedQuery, confidence)
  }
}

