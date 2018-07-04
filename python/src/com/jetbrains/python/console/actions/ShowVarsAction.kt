// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.console.PythonConsoleView

/**
 * Created by Yuli Fiterman on 9/18/2016.
 */
class ShowVarsAction(private val consoleView: PythonConsoleView, private val consoleComm: PydevConsoleCommunication) : ToggleAction("Show Variables", "Shows active console variables", AllIcons.Debugger.Watch), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return consoleView.isShowVars
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    consoleView.isShowVars = state

    if (state) {
      consoleView.showVariables(consoleComm)
    }
    else {
      consoleView.restoreWindow()
    }
  }
}
