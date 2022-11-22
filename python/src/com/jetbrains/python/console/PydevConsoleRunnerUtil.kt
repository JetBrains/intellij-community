// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PydevConsoleRunnerUtil")

package com.jetbrains.python.console

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.value.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.console.completion.PydevConsoleElement
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.parsing.console.PythonConsoleData
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.target.getPathMapper
import com.jetbrains.python.run.toStringLiteral
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import java.util.function.Function

fun getPathMapper(project: Project,
                  sdk: Sdk?,
                  consoleSettings: PyConsoleSettings): PyRemotePathMapper? {
  if (sdk == null) return null
  return when (val sdkAdditionalData = sdk.sdkAdditionalData) {
    is PyTargetAwareAdditionalData -> getPathMapper(project, consoleSettings, sdkAdditionalData)
    is PyRemoteSdkAdditionalDataBase -> getPathMapper(project, consoleSettings, sdkAdditionalData)
    else -> null
  }
}

fun getPathMapper(project: Project,
                  consoleSettings: PyConsoleSettings,
                  remoteSdkAdditionalData: PyRemoteSdkAdditionalDataBase): PyRemotePathMapper {
  val remotePathMapper = PythonRemoteInterpreterManager.appendBasicMappings(project, null, remoteSdkAdditionalData)
  val mappingSettings = consoleSettings.mappingSettings
  if (mappingSettings != null) {
    remotePathMapper.addAll(mappingSettings.pathMappings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return remotePathMapper
}

fun findPythonSdkAndModule(project: Project, contextModule: Module?): Pair<Sdk?, Module?> {
  var sdk: Sdk? = null
  var module: Module? = null
  val settings = PyConsoleOptions.getInstance(project).pythonConsoleSettings
  val sdkHome = settings.sdkHome
  if (sdkHome != null) {
    sdk = PythonSdkUtil.findSdkByPath(sdkHome)
    if (settings.moduleName != null) {
      module = ModuleManager.getInstance(project).findModuleByName(settings.moduleName)
    }
    else {
      module = contextModule
      if (module == null && ModuleManager.getInstance(project).modules.isNotEmpty()) {
        module = ModuleManager.getInstance(project).modules[0]
      }
    }
  }
  if (sdk == null && settings.isUseModuleSdk) {
    if (contextModule != null) {
      module = contextModule
    }
    else if (settings.moduleName != null) {
      module = ModuleManager.getInstance(project).findModuleByName(settings.moduleName)
    }
    if (module != null) {
      if (PythonSdkUtil.findPythonSdk(module) != null) {
        sdk = PythonSdkUtil.findPythonSdk(module)
      }
    }
  }
  else if (contextModule != null) {
    if (module == null) {
      module = contextModule
    }
    if (sdk == null) {
      sdk = PythonSdkUtil.findPythonSdk(module)
    }
  }
  if (sdk == null) {
    for (m in ModuleManager.getInstance(project).modules) {
      if (PythonSdkUtil.findPythonSdk(m) != null) {
        sdk = PythonSdkUtil.findPythonSdk(m)
        module = m
        break
      }
    }
  }
  if (sdk == null) {
    if (PythonSdkUtil.getAllSdks().size > 0) {
      sdk = PythonSdkUtil.getAllSdks()[0] //take any python sdk
    }
  }
  return Pair.create(sdk, module)
}

fun constructPyPathAndWorkingDirCommand(pythonPath: MutableCollection<String>,
                                        workingDir: String?,
                                        command: String): String {
  if (workingDir != null) {
    pythonPath.add(workingDir)
  }
  val path = pythonPath.joinToString(separator = ", ", transform = String::toStringLiteral)
  return command.replace(PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS, path)
}

fun constructPyPathAndWorkingDirCommand(pythonPath: MutableCollection<Function<TargetEnvironment, String>>,
                                        workingDirFunction: TargetEnvironmentFunction<String>?,
                                        command: String): TargetEnvironmentFunction<String> {
  if (workingDirFunction != null) {
    pythonPath.add(workingDirFunction)
  }
  val path = pythonPath.toLinkedSetFunction().andThenJoinToString(separator = ", ", transform = String::toStringLiteral)
  return ReplaceSubstringFunction(command, PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS, path)
}

private class ReplaceSubstringFunction(private val s: String,
                                       private val oldValue: String,
                                       private val newValue: TargetEnvironmentFunction<String>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = s.replace(oldValue, newValue.apply(t))

  override fun toString(): String = "ReplaceSubstringFunction(s='$s', oldValue='$oldValue', newValue=$newValue)"
}

fun addDefaultEnvironments(sdk: Sdk,
                           envs: Map<String, String>): Map<String, String> {
  setCorrectStdOutEncoding(envs)
  PythonEnvUtil.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk))
  return envs
}

/**
 * Add required ENV var to Python task to set its stdout charset to UTF-8 to allow it print correctly.
 *
 * @param envs    map of envs to add variable
 */
private fun setCorrectStdOutEncoding(envs: Map<String, String>) {
  val defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET;
  val encoding = defaultCharset.name()
  PythonEnvUtil.setPythonIOEncoding(PythonEnvUtil.setPythonUnbuffered(envs), encoding)
}

/**
 * Set command line charset as UTF-8 (the only charset supported by console)
 * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
 *
 * @param commandLine command line
 */
fun setCorrectStdOutEncoding(commandLine: GeneralCommandLine) {
  val defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET;
  commandLine.charset = defaultCharset
  PythonEnvUtil.setPythonIOEncoding(commandLine.environment, defaultCharset.name())
}

fun isInPydevConsole(element: PsiElement): Boolean {
  return element is PydevConsoleElement || getConsoleCommunication(element) != null || hasConsoleKey(element)
}

private fun hasConsoleKey(element: PsiElement): Boolean {
  val psiFile = element.containingFile ?: return false
  if (psiFile.virtualFile == null) return false
  val inConsole = element.containingFile.virtualFile.getUserData(PythonConsoleView.CONSOLE_KEY)
  return inConsole != null && inConsole
}

fun isConsoleView(file: VirtualFile): Boolean {
  return file.getUserData(PythonConsoleView.CONSOLE_KEY) == true
}

fun getPythonConsoleData(element: ASTNode?): PythonConsoleData? {
  if (element == null || element.psi == null || element.psi.containingFile == null) {
    return null
  }
  val file = PydevConsoleRunnerImpl.getConsoleFile(element.psi.containingFile) ?: return null
  return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA)
}

private fun getConsoleCommunication(element: PsiElement): ConsoleCommunication? {
  val containingFile = element.containingFile
  return containingFile?.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY)
}

fun getConsoleSdk(element: PsiElement): Sdk? {
  val containingFile = element.containingFile
  return containingFile?.getCopyableUserData(PydevConsoleRunner.CONSOLE_SDK)
}