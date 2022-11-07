// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel
import com.intellij.spellchecker.state.ProjectDictionaryState

internal class SettingsTransferActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    val settings = SpellCheckerSettings.getInstance(project)
    if (settings.isSettingsTransferred) {
      return
    }
    if (settings.isUseSingleDictionaryToSave && DictionaryLevel.PROJECT.getName() == settings.dictionaryToSave &&
        project.getService(ProjectDictionaryState::class.java).projectDictionary.words.isEmpty()) {
      settings.dictionaryToSave = DictionaryLevel.APP.getName()
    }
    settings.isSettingsTransferred = true
  }
}