// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.activationEnvironment

class PyRunAnythingCommandCustomizer : RunAnythingCommandCustomizer() {
  override fun customizeCommandLine(workDirectory: VirtualFile,
                                    dataContext: DataContext,
                                    commandLine: GeneralCommandLine): GeneralCommandLine {
    val file = dataContext.virtualFile ?: return commandLine
    val project = dataContext.project
    // Peeks at the snapshot, so a file without an interpreter shows no progress. Before the first one, nothing is activated.
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return commandLine
    val interpreter = runReadActionBlocking { snapshot.forFile(file, mainForOrphans = false) }?.interpreter ?: return commandLine
    // Run Anything calls this method on the EDT, so the activation environment must be read under a modal progress.
    val activationEnvironment = runWithModalProgressBlocking(project, PyBundle.message("progress.title.reading.activation.environment")) {
      @Suppress("DEPRECATION") // The activation environment is read from an SDK.
      interpreter.getSdkAPI().activationEnvironment().successOrNull
    } ?: return commandLine
    PythonSdkType.applyActivationEnvironment(commandLine.environment, activationEnvironment)
    commandLine.findExecutableInPath()?.let {
      commandLine.exePath = it
    }
    return commandLine
  }
}
