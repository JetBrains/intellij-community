// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.nlp.similarity.Levenshtein
import ai.grazie.spell.lists.WordList

internal class WordListAdapter : WordList, EditableWordListAdapter() {
  fun isAlien(word: String): Boolean {
    return !dictionaries.values.any { it.contains(word) ?: false } && !aggregator.contains(word)
  }

  override fun contains(word: String, caseSensitive: Boolean): Boolean {
    val inDictionary = if (caseSensitive) {
      dictionaries.values.any { it.contains(word) ?: false }
    } else {
      val lowered = word.lowercase()
      // NOTE: dictionary may not contain a lowercase form, but may contain any form in a different case
      // current dictionary interface does not support caseSensitive
      dictionaries.values.any { (it.contains(word) ?: false) || it.contains(lowered) ?: false }
    }

    return inDictionary || aggregator.contains(word, caseSensitive)
  }

  override fun suggest(word: String): LinkedHashSet<String> {
    val result = LinkedHashSet<String>()
    for (dictionary in dictionaries.values) {
      dictionary.consumeSuggestions(word) {
        if (it.isEmpty()) {
          return@consumeSuggestions
        }
        val distance = Levenshtein.distance(word, it, SimpleWordList.MAX_LEVENSHTEIN_DISTANCE + 1)
        if (distance <= SimpleWordList.MAX_LEVENSHTEIN_DISTANCE) {
          result.add(it)
        }
      }
    }

    result.addAll(aggregator.suggest(word))
    result.remove("")
    return result
  }
}
