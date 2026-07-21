// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.repository.PyPackageRepositoriesConfigurable

internal class PyRepositoriesAction : DumbAwareAction(PyBundle.messagePointer("python.toolwindow.packages.repositories.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ShowSettingsUtil.getInstance().showSettingsDialog(project, PyPackageRepositoriesConfigurable::class.java)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
