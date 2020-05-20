// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.SuggestionProvider
import com.intellij.spellchecker.util.Strings

internal class GrazieSuggestionProvider(private val engine: SpellCheckerEngine) : SuggestionProvider {
  override fun getSuggestions(text: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    val suggestions = engine.getSuggestions(text, maxSuggestions, maxMetrics)

    return alignSuggestionsToWord(text, suggestions)
  }

  private fun alignSuggestionsToWord(word: String, suggestions: List<String>): List<String> {
    if (suggestions.isEmpty()) return emptyList()
    if (engine.isCorrect(word)) return emptyList()

    if (Strings.isCapitalized(word)) {
      Strings.capitalize(suggestions)
    }
    else if (Strings.isUpperCase(word)) {
      Strings.upperCase(suggestions)
    }

    return suggestions.distinct()
  }
}