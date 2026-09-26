// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenNewFileFeatureBackend
import com.intellij.python.community.common.welcomeScreen.WelcomeScreenFeatureIds

internal class PyNewScriptWelcomeScreenFeature : WelcomeScreenNewFileFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.NEW_SCRIPT

  override val languageId: String = "Python"
}

internal class PyImportFileWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.IMPORT_FILE

  override fun onClick(project: Project) {
    val action = ActionManager.getInstance().getAction("OpenFile")
    val event = createEvent(action, SimpleDataContext.getProjectContext(project), null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
    action.actionPerformed(event)
  }
}