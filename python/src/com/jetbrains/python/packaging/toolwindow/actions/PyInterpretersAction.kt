// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.interpreter.InterpreterSettingsQuickFix

internal class PyInterpretersAction : DumbAwareAction(PyBundle.messagePointer("python.toolwindow.packages.interpreters.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
