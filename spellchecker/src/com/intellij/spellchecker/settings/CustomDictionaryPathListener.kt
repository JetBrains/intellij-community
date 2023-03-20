package com.intellij.spellchecker.settings

import com.intellij.util.messages.Topic

// used in Rider
interface CustomDictionaryPathListener {
  fun dictionariesChanged(paths: List<String>)

  companion object {
    val TOPIC: Topic<CustomDictionaryPathListener> = Topic(CustomDictionaryPathListener::class.java)
  }
}