/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * @author traff
 */
class PythonConsoleToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val isStartedFromRunner = toolWindow.component.getClientProperty(PydevConsoleRunnerImpl.STARTED_BY_RUNNER)
    // we need it to distinguish Console toolwindows started by Console Runner from ones started by toolwindow activation
    if (isStartedFromRunner != "true") {
      val runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
      TransactionGuard.submitTransaction(project, Runnable { runner.runSync(true) })
    }
  }

  companion object {
    val ID: String = "Python Console"
  }
}
