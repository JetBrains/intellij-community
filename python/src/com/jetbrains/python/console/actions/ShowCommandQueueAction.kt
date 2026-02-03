// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.actions

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.PyConsoleUtil
import com.jetbrains.python.console.PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY
import com.jetbrains.python.console.PythonConsoleView
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon

/***
 * Action for showing the CommandQueue window
 */
class ShowCommandQueueAction(private val consoleView: PythonConsoleView)
  : ToggleAction(PyBundle.message("python.console.command.queue.show.action.text"),
                 PyBundle.message("python.console.command.queue.show.action.description"),
                 emptyQueueIcon), DumbAware {

  companion object {
    private val emptyQueueIcon = PythonIcons.Python.CommandQueue
    private val notEmptyQueueIcon = ExecutionUtil.getLiveIndicator(emptyQueueIcon)

    @JvmStatic
    fun isCommandQueueIcon(icon: Icon): Boolean = icon == emptyQueueIcon || icon == notEmptyQueueIcon
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val communication = consoleView.file.getCopyableUserData(CONSOLE_COMMUNICATION_KEY)
    communication?.let {
      if (PyConsoleUtil.isCommandQueueEnabled(consoleView.project)) {
        e.presentation.isEnabled = true

        if (PyConsoleUtil.isCommandQueueEmpty(communication)) {
          e.presentation.icon = emptyQueueIcon
        }
        else {
          e.presentation.icon = notEmptyQueueIcon
        }
      }
      else {
        e.presentation.icon = emptyQueueIcon
        e.presentation.isEnabled = false
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

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