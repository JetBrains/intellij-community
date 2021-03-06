// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingAnActionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.RunPythonOrDebugConsoleAction
import icons.PythonIcons
import javax.swing.Icon

/**
 * @author vlan
 */
class PyConsoleRunAnythingProvider : RunAnythingAnActionProvider<RunPythonOrDebugConsoleAction>() {
  override fun getCommand(value: RunPythonOrDebugConsoleAction) = helpCommand

  override fun getHelpCommand() = "python"

  override fun getHelpGroupTitle(): String? = "Python" // NON-NLS

  override fun getValues(dataContext: DataContext, pattern: String): Collection<RunPythonOrDebugConsoleAction> {
    val action = ActionManager.getInstance().getAction("com.jetbrains.python.console.RunPythonConsoleAction")
    return listOfNotNull(action as? RunPythonOrDebugConsoleAction)
  }

  override fun getHelpIcon(): Icon = PythonIcons.Python.PythonConsole

  override fun getHelpDescription(): String = PyBundle.message("python.console.run.anything.provider")
}