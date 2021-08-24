// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import com.intellij.grazie.speller.lists.TraversableWordList
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.containers.CollectionFactory

internal abstract class EditableWordListAdapter {
  protected val dictionaries: MutableMap<String, Dictionary> = CollectionFactory.createSmallMemoryFootprintMap()
  protected val traversable = EditableAggregatedWordList()

  val names: Set<String>
    get() = traversable.keys + dictionaries.keys

  fun addDictionary(dictionary: Dictionary) {
    dictionaries.put(dictionary.name, dictionary)
  }

  fun addList(name: String, list: TraversableWordList) {
    traversable.addList(name, list)
  }

  fun containsSource(name: String): Boolean = dictionaries.containsKey(name) || traversable.containsList(name)
  fun removeSource(name: String) {
    dictionaries.remove(name)
    traversable.removeList(name)
  }

  fun reset() {
    dictionaries.clear()
    traversable.clear()
  }
}