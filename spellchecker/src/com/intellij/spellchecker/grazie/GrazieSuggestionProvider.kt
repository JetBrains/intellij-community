// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.SuggestionProvider

internal class GrazieSuggestionProvider(private val engine: SpellCheckerEngine) : SuggestionProvider {
  override fun getSuggestions(text: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    return engine.getSuggestions(text, maxSuggestions, maxMetrics).distinct()
  }
}