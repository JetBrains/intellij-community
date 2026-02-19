// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.python.actions.executeCodeInConsole
import com.jetbrains.python.console.PythonConsoleToolWindowFactory
import com.jetbrains.python.icons.PythonIcons

internal class RunPythonToolwindowAction : AnAction(
  PythonIcons.Python.PythonConsoleToolWindow), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return
    executeCodeInConsole(project, null, null, false, false, false, null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  /*
  * This action should be available only when Python Console tool window isn't registered yet
  * It's used only in Python plugin, because Console tool window is available by default in PyCharm
  */
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) return
    e.presentation.isEnabledAndVisible = ToolWindowManager.getInstance(project).getToolWindow(PythonConsoleToolWindowFactory.ID) == null
  }
}