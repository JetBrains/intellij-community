// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.spell.lists.WordList
import com.intellij.spellchecker.dictionary.Dictionary
import java.util.concurrent.ConcurrentHashMap

internal abstract class EditableWordListAdapter {
  protected val dictionaries = ConcurrentHashMap<String, Dictionary>()
  protected val aggregator: EditableAggregatedWordList = EditableAggregatedWordList()

  val names: Set<String>
    get() = aggregator.keys + dictionaries.keys

  fun addDictionary(dictionary: Dictionary) {
    dictionaries[dictionary.name] = dictionary
  }

  fun addList(name: String, list: WordList) {
    aggregator.addList(name, list)
  }

  fun getDictionary(name: String): Dictionary? = dictionaries[name]

  fun containsSource(name: String): Boolean = dictionaries.containsKey(name) || aggregator.containsList(name)

  fun removeSource(name: String) {
    dictionaries.remove(name)
    aggregator.removeList(name)
  }

  fun reset() {
    dictionaries.clear()
    aggregator.clear()
  }
}