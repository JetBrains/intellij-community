// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingAnActionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.console.RunPythonOrDebugConsoleAction
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon

class PyConsoleRunAnythingProvider : RunAnythingAnActionProvider<RunPythonOrDebugConsoleAction>() {
  override fun getCommand(value: RunPythonOrDebugConsoleAction) = helpCommand

  override fun getHelpCommand() = "python"

  override fun getHelpGroupTitle(): String = "Python" // NON-NLS

  override fun getValues(dataContext: DataContext, pattern: String): Collection<RunPythonOrDebugConsoleAction> {
    val action = ActionManager.getInstance().getAction("com.jetbrains.python.console.RunPythonOrDebugConsoleAction")
    return listOfNotNull(action as? RunPythonOrDebugConsoleAction)
  }

  override fun getHelpIcon(): Icon = PythonIcons.Python.PythonConsole
}