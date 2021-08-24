// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.async

import com.intellij.grazie.speller.Speller
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.engine.SpellCheckerEngineListener

/**
 * Async version of Grazie speller
 * It will create speller with all its bundled dictionaries in background thread and restart inspections.
 *
 * Until then all words are considered alien.
 */
internal class GrazieAsyncSpeller(project: Project, private val create: () -> Speller) : Speller {
  private var speller: Speller? = null

  init {
    AsyncUtils.run(project) {
      if (!project.isDisposed) {
        speller = create()
        project.messageBus.syncPublisher(SpellCheckerEngineListener.TOPIC).onSpellerInitialized()
      }
    }
  }

  override fun isAlien(word: String): Boolean {
    return speller?.isAlien(word) ?: true
  }

  override fun isMisspelled(word: String): Boolean {
    return speller?.isMisspelled(word) ?: false
  }

  override fun suggestAndRank(word: String, max: Int): Map<String, Double> {
    return speller?.suggestAndRank(word, max) ?: emptyMap()
  }
}
