// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.spell.lists.WordList
import java.util.concurrent.ConcurrentHashMap

internal class EditableAggregatedWordList : WordList {
  private val aggregated = ConcurrentHashMap<String, WordList>()

  val keys: Set<String>
    get() = aggregated.keys

  override fun suggest(word: String): LinkedHashSet<String> = aggregated.values.flatMapTo(LinkedHashSet()) { it.suggest(word) }

  override fun contains(word: String, caseSensitive: Boolean): Boolean = aggregated.values.any { it.contains(word, caseSensitive) }

  fun containsList(name: String): Boolean = name in keys

  fun addList(name: String, list: WordList) {
    aggregated.put(name, list)
  }

  fun removeList(name: String) {
    aggregated.remove(name)
  }

  fun clear() {
    aggregated.clear()
  }
}
