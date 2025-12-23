package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.projectModelEnabled

internal class PyProjectTomlSyncAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!projectModelEnabled || project.isDefault) { // Service doesn't support default project
      return
    }
    project.service<PyProjectAutoImportService>().refresh()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && projectModelEnabled && !project.isDefault
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}