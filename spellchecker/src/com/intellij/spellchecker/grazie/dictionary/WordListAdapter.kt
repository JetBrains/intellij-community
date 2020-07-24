// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import com.intellij.grazie.speller.lists.WordList
import com.intellij.grazie.speller.utils.Distances

internal class WordListAdapter : WordList, EditableWordListAdapter() {
  override fun contains(word: String): Boolean {
    return dictionaries.values.any { it.contains(word) ?: false } || traversable.contains(word)
  }

  override fun isAlien(word: String): Boolean {
    return dictionaries.values.all { it.contains(word) == null } && traversable.isAlien(word)
  }

  override fun suggest(word: String, distance: Int): Sequence<String> = sequence {
    for (dictionary in dictionaries.values) {
      val set = HashSet<String>()
      dictionary.consumeSuggestions(word) {
        val cur = Distances.levenshtein.distance(word, it, distance + 1)
        if (cur <= distance) {
          set.add(it)
        }
      }
      yieldAll(set)
    }

    yieldAll(traversable.suggest(word, distance))
  }.distinct()

  override fun prefix(prefix: String): Sequence<String> = sequence {
    yieldAll(traversable.prefix(prefix))
  }
}