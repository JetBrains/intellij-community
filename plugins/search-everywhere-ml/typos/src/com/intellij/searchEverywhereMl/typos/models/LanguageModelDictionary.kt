package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.lists.WordList


internal interface LanguageModelDictionary : WordList, FrequencyMetadata {
  val totalFrequency: Int
  val allWords: Set<String>
  
  companion object {
    const val MAX_LEVENSHTEIN_DISTANCE = 3
  }
}
