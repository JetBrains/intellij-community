// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.PythonSdkType

/**
 * @author vlan
 */
class PyRunAnythingCommandCustomizer : RunAnythingCommandCustomizer() {
  override fun customizeCommandLine(workDirectory: VirtualFile,
                                    dataContext: DataContext,
                                    commandLine: GeneralCommandLine): GeneralCommandLine {
    dataContext.virtualFile?.findPythonSdk(dataContext.project)?.let { sdk ->
      PythonSdkType.patchCommandLineForVirtualenv(commandLine, sdk)
      commandLine.findExecutableInPath()?.let {
        commandLine.exePath = it
      }
    }
    return commandLine
  }
}