// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog

internal class PyInstallPackageAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    // The install dialog needs an SDK to know what to install into / where to write the
    // dependency entry. Without one, the dialog can't do anything useful, so hide the action.
    val project = e.project
    e.presentation.isEnabledAndVisible =
      project != null && project.service<PyPackagingToolWindowService>().currentSdk != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // Defensive guard for the shortcut path: `update` should have already disabled the action
    // when no SDK is available, but invoking it through a keymap binding bypasses that gate in
    // some contexts (Find Action / global shortcut), so re-check before showing the dialog.
    if (project.service<PyPackagingToolWindowService>().currentSdk == null) return
    PyInstallPackageDialog(project).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
