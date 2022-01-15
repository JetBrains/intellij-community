// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.PythonConsoleView
import icons.PythonIcons

/***
 * action for showing the CommandQueue window
 */
class ShowCommandQueueAction(private val consoleView: PythonConsoleView)
  : ToggleAction(PyBundle.message("python.console.command.queue.show.action.text"),
                 PyBundle.message("python.console.command.queue.show.action.description"),
                 PythonIcons.Python.CommandQueue), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    return consoleView.isShowQueue
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    consoleView.isShowQueue = state
    if (state) {
      consoleView.showQueue()
    }
    else {
      consoleView.restoreQueueWindow(false)
    }
  }
}