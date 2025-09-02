// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.ranker

import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import ai.grazie.utils.LinkedSet
import com.intellij.spellchecker.grazie.diacritic.Diacritics

class DiacriticSuggestionRanker(
  private val fallbackSuggestionRanker: SuggestionRanker,
) : SuggestionRanker {
  override fun score(word: String, suggestions: LinkedSet<String>): Map<String, Double> {
    val weights = suggestions.associateWith { score(word, it) }
    if (weights.filter { it.value > 0 }.isEmpty()) {
      return fallbackSuggestionRanker.score(word, suggestions)
    }
    return weights
  }
  
  private fun score(word: String, suggestion: String): Double {
    return if (Diacritics.equalsIgnoringDiacritics(word, suggestion)) 1.0 else 0.0
  }
}