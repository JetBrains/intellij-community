// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import com.intellij.grazie.speller.lists.MetadataWordList
import com.intellij.grazie.speller.lists.TraversableWordList
import com.intellij.grazie.speller.lists.WordList
import com.intellij.util.containers.BidirectionalMap

class EditableAggregatedWordList : WordList {
  private var words = MetadataWordList.Default<Array<Int>>(emptyMap())

  private val namesToIds = BidirectionalMap<String, Int>()

  val keys: Set<String>
    get() = namesToIds.keys

  override fun suggest(word: String, distance: Int): Sequence<String> = words.suggest(word, distance)

  override fun prefix(prefix: String): Sequence<String> = words.prefix(prefix)

  override fun contains(word: String): Boolean = words.contains(word)

  override fun isAlien(word: String): Boolean = words.isAlien(word)

  fun containsList(name: String): Boolean = name in keys

  @Synchronized
  fun addList(name: String, list: TraversableWordList) {
    //if list already loaded -- reload it
    if (containsList(name)) removeList(name)

    val current = words.all.map { it to words.getMetadata(it)!! }.toMap(HashMap())

    val id = (namesToIds.values.max() ?: -1) + 1
    namesToIds[name] = id

    for (word in list.all) {
      current[word] = current[word]?.plus(id) ?: arrayOf(id)
    }

    words = MetadataWordList.Default(current)
  }

  @Synchronized
  fun removeList(name: String) {
    if (!containsList(name)) return

    val id = namesToIds[name] ?: return

    val current = words.all.map { it to words.getMetadata(it)!! }.toMap(HashMap())

    for ((value, ids) in current) {
      if (id in ids) {
        current[value] = ids.filter { it != id }.toTypedArray()
      }
    }

    words = MetadataWordList.Default(current.filterValues { it.isNotEmpty() })
  }

  @Synchronized
  fun clear() {
    words = MetadataWordList.Default(emptyMap())
  }
}