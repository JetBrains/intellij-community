// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.util.messages.Topic

// used in Rider
interface CustomDictionaryPathListener {
  fun dictionariesChanged(paths: List<String>)

  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<CustomDictionaryPathListener> = Topic(CustomDictionaryPathListener::class.java)
  }
}