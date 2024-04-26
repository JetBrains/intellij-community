// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.spellchecker.ApplicationDictionaryLayer
import com.intellij.spellchecker.ProjectDictionaryLayer
import com.intellij.spellchecker.state.ProjectDictionaryState

private class SettingsTransferActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val settings = project.serviceAsync<SpellCheckerSettings>()
    if (settings.isSettingsTransferred) {
      return
    }

    if (settings.isUseSingleDictionaryToSave && ProjectDictionaryLayer.name.get() == settings.dictionaryToSave &&
        project.serviceAsync<ProjectDictionaryState>().projectDictionary.words.isEmpty()) {
      settings.dictionaryToSave = ApplicationDictionaryLayer.name
    }
    settings.isSettingsTransferred = true
  }
}