package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class PyProjectTomlSyncAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!projectModelEnabled) {
      return
    }
    linkProjectWithProgressInBackground(project)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && projectModelEnabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}