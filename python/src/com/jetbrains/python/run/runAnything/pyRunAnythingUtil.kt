// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * @author vlan
 */
internal val DataContext.project: Project
  get() = RunAnythingUtil.fetchProject(this)

internal val DataContext.virtualFile: VirtualFile?
  get() = CommonDataKeys.VIRTUAL_FILE.getData(this)

internal fun VirtualFile.findPythonSdk(project: Project): Sdk? {
  val module = ModuleUtil.findModuleForFile(this, project)
  return PythonSdkUtil.findPythonSdk(module)
}

internal fun GeneralCommandLine.findExecutableInPath(): String? {
  val executable = exePath
  if ("/" in executable || "\\" in executable) return executable
  val paths = listOfNotNull(effectiveEnvironment["PATH"], System.getenv("PATH"), EnvironmentUtil.getValue("PATH"))
  return paths
    .asSequence()
    .mapNotNull { path ->
      if (SystemInfo.isWindows) {
        PathEnvironmentVariableUtil.getWindowsExecutableFileExtensions()
          .mapNotNull { ext -> PathEnvironmentVariableUtil.findInPath("$executable$ext", path, null)?.path }
          .firstOrNull()
      }
      else {
        PathEnvironmentVariableUtil.findInPath(executable, path, null)?.path
      }
    }
    .firstOrNull()
}
