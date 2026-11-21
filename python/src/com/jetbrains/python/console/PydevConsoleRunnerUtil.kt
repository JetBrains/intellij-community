// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PydevConsoleRunnerUtil")

package com.jetbrains.python.console

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.TraceableTargetEnvironmentFunction
import com.intellij.execution.target.value.andThenJoinToString
import com.intellij.execution.target.value.toLinkedSetFunction
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.createMapper
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.parsing.console.PythonConsoleData
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.toStringLiteral
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.asModuleOrProject
import com.jetbrains.python.sdk.findPyProject
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

/**
 * Creates [PyRemotePathMapper] for Python Console execution on a target.
 *
 * @param project the project Python Console is started for
 * @param interpreter the interpreter that Python Console is started with
 * @param consoleSettings Python Console settings
 * @param targetEnvironment the target environment to add upload volumes to the result path mapper
 */

@RequiresBackgroundThread
internal fun createTargetEnvironmentPathMapper(project: Project,
                                      interpreter: PythonInterpreter,
                                      consoleSettings: PyConsoleSettings,
                                      targetEnvironment: TargetEnvironment): PyRemotePathMapper {
  val pathMapper = getPathMapper(project, interpreter, consoleSettings)
  for (volume in targetEnvironment.uploadVolumes.values) {
    pathMapper.addMapping(volume.localRoot.toString(), volume.targetRoot, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return pathMapper
}

@RequiresBackgroundThread
internal fun getPathMapper(project: Project,
                  interpreter: PythonInterpreter,
                  consoleSettings: PyConsoleSettings): PyRemotePathMapper =
  runBlockingMaybeCancellable { interpreter.createMapper(consoleSettings.mappingSettings) }

/**
 * The interpreter a console starts on, and the [ModuleOrProject] it runs for. The interpreter is `null` when the
 * project offers none.
 *
 * The interpreter is the one of the subproject the console runs in: the module of [moduleOrProject] when it has one,
 * and otherwise the subproject [getModuleToStartConsole] names, which is the interpreter widget's own rule. One source
 * for every surface, so the console, the widget and the packages tool window never disagree.
 * For [ModuleOrProject.ProjectOnly] without such a subproject, the result keeps [moduleOrProject].
 */
internal suspend fun findPythonSdkAndModule(moduleOrProject: ModuleOrProject): Pair<PythonInterpreter?, ModuleOrProject> {
  val target = when (moduleOrProject) {
    is ModuleOrProject.ModuleAndProject -> moduleOrProject
    is ModuleOrProject.ProjectOnly -> findPyProjectToStartConsole(moduleOrProject.project)?.let { ModuleOrProject.ModuleAndProject(it) }
                                      ?: moduleOrProject
  }
  return target.findPyProject()?.getInterpreter() to target
}

/** [ModuleOrProject] for [module] when it is not `null`, and for [project] otherwise. */
internal fun toModuleOrProject(project: Project, module: Module?): ModuleOrProject =
  module?.asModuleOrProject ?: project.asModuleOrProject

@ApiStatus.Internal
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

internal class ReplaceSubstringsFunction(private val s: String,
                                private val replaces: List<kotlin.Pair<String, TargetEnvironmentFunction<String>>>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String {
    var res = s
    replaces.forEach { res = res.replace(it.first, it.second.apply(t)) }
    return res
  }

  override fun toString(): String = "ReplaceSubstringsFunction(s='$s', oldValues=${replaces.map { it.first }}, newValues=${replaces.map { it.second }})"
}

/**
 * Sets the stdout encoding of a console process, and puts the paths that the user added to the interpreter of [interpreter]
 * on its `PYTHONPATH`.
 */
@ApiStatus.Internal
fun addDefaultEnvironments(interpreter: PythonInterpreter, envs: Map<String, String>): Map<String, String> {
  setCorrectStdOutEncoding(envs)
  PythonEnvUtil.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(interpreter))
  return envs
}

/**
 * Add required ENV var to Python task to set its stdout charset to UTF-8 to allow it print correctly.
 *
 * @param envs    map of envs to add variable
 */
internal fun setCorrectStdOutEncoding(envs: Map<String, String>) {
  val defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET
  val encoding = defaultCharset.name()
  PythonEnvUtil.setPythonIOEncoding(PythonEnvUtil.setPythonUnbuffered(envs), encoding)
}

/**
 * Set command line charset as UTF-8 (the only charset supported by console)
 * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
 *
 * @param commandLine command line
 */
internal fun setCorrectStdOutEncoding(commandLine: GeneralCommandLine) {
  val defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET
  commandLine.charset = defaultCharset
  PythonEnvUtil.setPythonIOEncoding(commandLine.environment, defaultCharset.name())
}

@ApiStatus.Internal
fun isConsoleView(file: VirtualFile): Boolean {
  return file.getUserData(PythonConsoleView.CONSOLE_KEY) == true
}

internal fun getPythonConsoleData(element: ASTNode?): PythonConsoleData? {
  if (element == null || element.psi == null || element.psi.containingFile == null) {
    return null
  }
  val file = PydevConsoleRunnerImpl.getConsoleFile(element.psi.containingFile) ?: return null
  return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA)
}

internal fun getConsoleInterpreter(element: PsiElement): PythonInterpreter? {
  val containingFile = element.containingFile
  return containingFile?.getCopyableUserData(PydevConsoleRunner.CONSOLE_INTERPRETER)
}

@ApiStatus.Internal
suspend fun getModuleToStartConsole(project: Project): Module? = findPyProjectToStartConsole(project)?.residesOnModule

private suspend fun findPyProjectToStartConsole(project: Project): PyProject? {
  val selectedFile = readAction { FileEditorManager.getInstance(project).selectedFiles.firstOrNull() }
  return resolveConsoleTarget(project, selectedFile)?.pyProject
}

/**
 * Accepts text that may be wrapped in TC message. Unwraps it and removes TC escape code.
 * Regular text is unchanged
 */
@ApiStatus.Internal
fun processTCMessage(text: String): String {
  val parsedMessage = ServiceMessage.parse(text.trim()) ?: return text // Not a TC message
  return when (parsedMessage) {
    is TestStdOut -> parsedMessage.stdOut // TC with stdout
    is TestStdErr -> parsedMessage.stdErr // TC with stderr
    else -> "" // TC with out of any output
  }
}
