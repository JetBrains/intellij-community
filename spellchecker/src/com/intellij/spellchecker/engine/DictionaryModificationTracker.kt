// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * Tracks modifications to spell checker dictionaries.
 * This counter is incremented whenever user dictionaries or engine dictionaries are modified.
 */
@Service(Service.Level.PROJECT)
class DictionaryModificationTracker : SimpleModificationTracker() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): DictionaryModificationTracker = project.getService(DictionaryModificationTracker::class.java)
  }
}
