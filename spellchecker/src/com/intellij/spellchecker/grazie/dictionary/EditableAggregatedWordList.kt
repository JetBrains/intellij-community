// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.spell.lists.WordList
import java.util.concurrent.ConcurrentHashMap

class EditableAggregatedWordList : WordList {
  private val aggregated: ConcurrentHashMap<String, WordList> = ConcurrentHashMap()

  val keys: Set<String>
    get() = aggregated.keys

  override fun suggest(word: String): LinkedHashSet<String> = aggregated.values.flatMapTo(LinkedHashSet()) { it.suggest(word) }

  override fun contains(word: String, caseSensitive: Boolean): Boolean = aggregated.values.any { it.contains(word, caseSensitive) }

  fun containsList(name: String): Boolean = name in keys

  fun addList(name: String, list: WordList) {
    aggregated[name] = list
  }

  fun removeList(name: String) = aggregated.remove(name)

  fun clear() = aggregated.clear()
}
