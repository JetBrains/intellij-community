// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.spell.lists.WordList
import ai.grazie.spell.utils.Distances
import com.intellij.util.containers.CollectionFactory
import kotlin.collections.LinkedHashSet

class SimpleWordList(private val container: Set<String>) : WordList {
  companion object {
    const val MAX_LEVENSHTEIN_DISTANCE = 3
  }

  private val invariants = container.mapTo(CollectionFactory.createSmallMemoryFootprintSet()) { it.lowercase() }

  override fun contains(word: String, caseSensitive: Boolean): Boolean {
    return if (caseSensitive) container.contains(word) else invariants.contains(word.lowercase())
  }

  override fun suggest(word: String) = container.filterTo(LinkedHashSet()) {
    Distances.levenshtein.distance(it, word, MAX_LEVENSHTEIN_DISTANCE + 1) <= MAX_LEVENSHTEIN_DISTANCE
  }
}
