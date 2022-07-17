// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie.dictionary

import ai.grazie.spell.lists.WordListWithFrequency

internal class ExtendedWordListWithFrequency(private val base: WordListWithFrequency,
                                             private val extension: WordListAdapter) : WordListWithFrequency {
  override val defaultFrequency: Int
    get() = base.defaultFrequency

  override val maxFrequency: Int
    get() = base.maxFrequency

  override fun getFrequency(word: String) = base.getFrequency(word)

  override fun contains(word: String, caseSensitive: Boolean) =
    base.contains(word, caseSensitive) || extension.contains(word, caseSensitive)

  override fun suggest(word: String) = base.suggest(word).apply { this += extension.suggest(word) }
}
