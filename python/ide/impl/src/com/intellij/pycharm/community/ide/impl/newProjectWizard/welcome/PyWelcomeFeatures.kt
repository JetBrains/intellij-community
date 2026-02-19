// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.editorTab.LearnIdeEditorTab
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.PyMiscService
import com.intellij.pycharm.community.ide.impl.miscProject.impl.MiscScriptFileType
import com.intellij.python.common.welcomeScreen.WelcomeScreenFeatureIds
import com.intellij.util.application

internal class PyNewScriptWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.NEW_SCRIPT

  override fun onClick(project: Project) {
    val scriptMiscType = MiscScriptFileType
    PyMiscService.getInstance().createMiscProject(project, scriptMiscType)
  }
}

internal class PyNewNotebookWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.NEW_NOTEBOOK

  override fun onClick(project: Project) {
    val notebookMiscType = MiscFileType.EP.extensionList.find { it.id == "newNotebook" } ?: return
    PyMiscService.getInstance().createMiscProject(project, notebookMiscType)
  }
}

internal class PyLearnWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.LEARN_IDE

  override fun onClick(project: Project) {
    LearnIdeEditorTab.openLearnIdeInEditorTab(project)
  }
}

internal class PyImportFileWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.IMPORT_FILE

  override fun onClick(project: Project) {
    val action = ActionManager.getInstance().getAction("OpenFile")
    val event = createEvent(action, SimpleDataContext.getProjectContext(project), null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
    action.actionPerformed(event)
  }
}

internal class PyPluginsWelcomeScreenFeature : WelcomeScreenFeatureBackend() {
  override val featureKey: String = WelcomeScreenFeatureIds.PLUGINS

  override fun onClick(project: Project) {
    application.invokeLater {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java)
    }
  }
}