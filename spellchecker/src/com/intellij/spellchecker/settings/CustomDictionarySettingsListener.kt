// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.util.messages.Topic
import java.util.*

/**
 * Provides notifications when custom dictionaries are changed in Settings.
 */
interface CustomDictionarySettingsListener : EventListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val CUSTOM_DICTIONARY_SETTINGS_TOPIC: Topic<CustomDictionarySettingsListener> = Topic(CustomDictionarySettingsListener::class.java, Topic.BroadcastDirection.NONE)
  }
  
  fun customDictionaryPathsChanged(newPaths: List<String>)
}
