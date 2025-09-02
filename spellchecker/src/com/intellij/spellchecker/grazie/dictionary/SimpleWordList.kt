// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.nlp.similarity.Levenshtein
import ai.grazie.spell.lists.WordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.CollectionFactory

internal class SimpleWordList(private val container: Set<String>) : WordList {
  companion object {
    const val MAX_LEVENSHTEIN_DISTANCE = 3
  }

  private val invariants = buildInvariants(container)

  private fun buildInvariants(container: Set<String>): Set<String> {
    val result = CollectionFactory.createSmallMemoryFootprintSet<String>(container.size)
    for (entry in container) {
      ProgressManager.checkCanceled()
      result.add(entry.lowercase())
    }
    return result
  }

  override fun contains(word: String, caseSensitive: Boolean): Boolean {
    return if (caseSensitive) container.contains(word) else invariants.contains(word.lowercase())
  }

  override fun suggest(word: String) = container.filterTo(LinkedHashSet()) {
    Levenshtein.distance(it, word, MAX_LEVENSHTEIN_DISTANCE + 1) <= MAX_LEVENSHTEIN_DISTANCE
  }
}
