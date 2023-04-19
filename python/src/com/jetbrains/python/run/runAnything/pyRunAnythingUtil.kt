// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.sdk.PythonSdkUtil

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

internal fun createPythonConfiguration(dataContext: DataContext,
                                       pattern: String,
                                       configurationFactory: ConfigurationFactory): RunnerAndConfigurationSettings {
  val runManager = RunManager.getInstance(dataContext.project)
  val settings = runManager.createConfiguration(pattern, configurationFactory)
  val commandLine = ParametersList.parse(pattern)
  val arguments = commandLine.drop(1)
  val configuration = settings.configuration as? PythonRunConfiguration
  configuration?.apply {
    val first = arguments.getOrNull(0)
    when {
      first == "-m" -> {
        scriptName = arguments.getOrNull(1)
        scriptParameters = ParametersList.join(arguments.drop(2))
        isModuleMode = true
      }
      first?.startsWith("-m") == true -> {
        scriptName = first.substring(2)
        scriptParameters = ParametersList.join(arguments.drop(1))
        isModuleMode = true
      }
      else -> {
        scriptName = first
        scriptParameters = ParametersList.join(arguments.drop(1))
      }
    }
    var workingDir = dataContext.virtualFile
    when (val executingContext = dataContext.getData(RunAnythingProviderBase.EXECUTING_CONTEXT)) {
      is RunAnythingContext.ProjectContext, is RunAnythingContext.ModuleContext -> {
        scriptName?.let {
          FilenameIndex.getVirtualFilesByName(it, getSearchScope(dataContext)).firstOrNull()?.let {
            workingDir = it.parent
          }
        }
        workingDir?.let {
          workingDirectory = it.canonicalPath
        }
      }
      is RunAnythingContext.RecentDirectoryContext -> {
        workingDirectory = executingContext.path
      }
      else -> {}
    }
    workingDir?.let {
      this.module = ModuleUtil.findModuleForFile(it, project)
      this.isUseModuleSdk = true
    }
  }
  settings.isTemporary = true
  return settings
}

fun isProjectOrModuleContext(dataContext: DataContext): Boolean {
  val executingContext = dataContext.getData(RunAnythingProviderBase.EXECUTING_CONTEXT)
  return executingContext is RunAnythingContext.ProjectContext ||
         executingContext is RunAnythingContext.ModuleContext
}

fun getSearchScope(dataContext: DataContext): GlobalSearchScope {
  when (val executingContext = dataContext.getData(RunAnythingProviderBase.EXECUTING_CONTEXT)) {
    is RunAnythingContext.ProjectContext -> return GlobalSearchScope.projectScope(executingContext.project)
    is RunAnythingContext.ModuleContext -> return GlobalSearchScope.moduleScope(executingContext.module)
    else -> return GlobalSearchScope.allScope(dataContext.project)
  }
}
