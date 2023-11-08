package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.spell.lists.WordList
import ai.grazie.spell.suggestion.ranker.SuggestionRanker

internal class TypoSuggestionProvider(private val dictionary: WordList,
                                      private val ranker: SuggestionRanker) {
  fun getSuggestions(word: String, maxSuggestions: Int): List<ScoredSuggestion> {
    if (!isMisspelled(word)) return emptyList()

    val suggestions = dictionary.suggest(word)

    return ranker.score(word, suggestions)
      .map { ScoredSuggestion(it.key, it.value) }
      .sortedDescending()
      .take(maxSuggestions)
  }

  fun isMisspelled(word: String): Boolean = dictionary.contains(word).not()

  data class ScoredSuggestion(val suggestion: CharSequence, val score: Double) : Comparable<ScoredSuggestion> {
    override fun compareTo(other: ScoredSuggestion): Int {
      return score.compareTo(other.score)
    }
  }
}