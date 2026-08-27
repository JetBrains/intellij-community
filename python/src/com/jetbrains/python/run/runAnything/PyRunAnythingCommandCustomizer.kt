// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.activationEnvironment

class PyRunAnythingCommandCustomizer : RunAnythingCommandCustomizer() {
  override fun customizeCommandLine(workDirectory: VirtualFile,
                                    dataContext: DataContext,
                                    commandLine: GeneralCommandLine): GeneralCommandLine {
    dataContext.virtualFile?.findPythonSdk(dataContext.project)?.let { sdk ->
      // Run Anything calls this method on the EDT, so the activation environment must be read under a modal progress.
      val activationEnvironment = runWithModalProgressBlocking(
        dataContext.project,
        PyBundle.message("progress.title.reading.activation.environment"),
      ) {
        sdk.activationEnvironment().successOrNull
      }
      PythonSdkType.applyActivationEnvironment(commandLine.environment, activationEnvironment)
      commandLine.findExecutableInPath()?.let {
        commandLine.exePath = it
      }
    }
    return commandLine
  }
}
