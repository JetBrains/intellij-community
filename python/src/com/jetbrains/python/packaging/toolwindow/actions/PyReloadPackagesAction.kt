// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService

internal class PyReloadPackagesAction : DumbAwareAction(PyBundle.messagePointer("python.toolwindow.packages.reload.packages.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val service = project.service<PyPackagingToolWindowService>()
    service.reloadPackages()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
