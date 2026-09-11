// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
class PythonConsoleToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val isStartedFromRunner = toolWindow.component.getClientProperty(PydevConsoleRunnerImpl.STARTED_BY_RUNNER)
    // we need it to distinguish Console toolwindows started by Console Runner from ones started by toolwindow activation
    if (isStartedFromRunner != "true") {
      // createToolWindowContent is @RequiresEdt and cannot suspend, while building the runner waits for the project
      // model. The runner therefore comes back on the EDT, which is also the write-safe context the former
      // TransactionGuard.submitTransaction stood for.
      launchPythonConsoleRunnerIfInterpreterExists(project, null) { it.runSync(true) }
    }
  }

  companion object {
    @NonNls
    const val ID: String = "Python Console"
  }
}
