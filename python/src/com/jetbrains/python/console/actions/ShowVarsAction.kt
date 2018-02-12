/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
class ShowVarsAction(private val consoleView: PythonConsoleView, private val consoleComm: PydevConsoleCommunication) : ToggleAction("Show Variables", "Shows active console variables", AllIcons.Debugger.Watches), DumbAware {

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
