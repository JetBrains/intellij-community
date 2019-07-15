// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingAnActionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.console.RunPythonConsoleAction
import icons.PythonIcons
import javax.swing.Icon

/**
 * @author vlan
 */
class PyConsoleRunAnythingProvider : RunAnythingAnActionProvider<RunPythonConsoleAction>() {
  override fun getCommand(value: RunPythonConsoleAction) = helpCommand

  override fun getHelpCommand() = "python"

  override fun getHelpGroupTitle(): String? = "Python"

  override fun getValues(dataContext: DataContext, pattern: String): Collection<RunPythonConsoleAction> {
    val action = ActionManager.getInstance().getAction("com.jetbrains.python.console.RunPythonConsoleAction")
    return listOfNotNull(action as? RunPythonConsoleAction)
  }

  override fun getHelpIcon(): Icon = PythonIcons.Python.PythonConsole

  override fun getHelpDescription(): String = "Runs Python console"
}