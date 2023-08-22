// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.python.PythonPluginDisposable
import org.jetbrains.annotations.NonNls

internal class PythonConsoleToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val isStartedFromRunner = toolWindow.component.getClientProperty(PydevConsoleRunnerImpl.STARTED_BY_RUNNER)
    // we need it to distinguish Console toolwindows started by Console Runner from ones started by toolwindow activation
    if (isStartedFromRunner != "true") {
      val runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
      TransactionGuard.submitTransaction(PythonPluginDisposable.getInstance(project), Runnable { runner.runSync(true) })
    }
  }

  companion object {
    @NonNls
    const val ID: String = "Python Console"
  }
}
