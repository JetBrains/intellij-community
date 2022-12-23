// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.async

import ai.grazie.spell.Speller
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.engine.SpellCheckerEngineListener

/**
 * Async version of Grazie speller
 * It will create speller with all its bundled dictionaries in background thread and restart inspections.
 *
 * Until then all words are considered alien.
 */
internal class GrazieAsyncSpeller(project: Project, private val create: suspend () -> Speller) : Speller {
  private var speller: Speller? = null

  init {
    AsyncUtils.run(project) {
      speller = create()

      readAction {
        project.messageBus.syncPublisher(SpellCheckerEngineListener.TOPIC).onSpellerInitialized()
      }
    }
  }

  val isCreated: Boolean
    get() = speller != null

  override fun isAlien(word: String): Boolean {
    return speller?.isAlien(word) ?: true
  }

  override fun isMisspelled(word: String, caseSensitive: Boolean): Boolean {
    return speller?.isMisspelled(word, caseSensitive) ?: false
  }

  override fun suggestAndRank(word: String, max: Int): Map<String, Double> {
    return speller?.suggestAndRank(word, max) ?: emptyMap()
  }
}
