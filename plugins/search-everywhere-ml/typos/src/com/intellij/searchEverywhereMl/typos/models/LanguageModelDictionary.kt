package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.nlp.similarity.Levenshtein
import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.lists.WordList
import com.intellij.grazie.utils.LinkedSet


internal interface LanguageModelDictionary : WordList, FrequencyMetadata {
  val allWords: Set<String>
}


internal class SimpleLanguageModelDictionary(private val words: Map<String, Int>) : LanguageModelDictionary {
  override val allWords: Set<String>
    get() = words.keys

  override val defaultFrequency: Int
    get() = 0

  override val maxFrequency: Int = words.values.max()

  override fun getFrequency(word: String): Int? = words[word]

  override fun contains(word: String, caseSensitive: Boolean): Boolean = word in words

  override fun suggest(word: String): LinkedSet<String> = words.keys
    .filterTo(LinkedHashSet()) {
      Levenshtein.distance(it, word, MAX_LEVENSHTEIN_DISTANCE + 1) <= MAX_LEVENSHTEIN_DISTANCE
    }

  companion object {
    const val MAX_LEVENSHTEIN_DISTANCE = 3
  }
}