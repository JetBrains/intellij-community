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
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.remote.RemoteMappingsManager
import com.intellij.remote.RemoteSdkProperties
import com.intellij.remote.TargetAwarePathMappingProvider
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.console.completion.PydevConsoleElement
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.parsing.console.PythonConsoleData
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.toStringLiteral
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

/**
 * Creates [PyRemotePathMapper] for Python Console execution on a target.
 *
 * @param project the project Python Console is started for
 * @param sdk Python SDK that Python Console is started with
 * @param consoleSettings Python Console settings
 * @param targetEnvironment the target environment to add upload volumes to the result path mapper
 */
internal fun createTargetEnvironmentPathMapper(project: Project,
                                      sdk: Sdk,
                                      consoleSettings: PyConsoleSettings,
                                      targetEnvironment: TargetEnvironment): PyRemotePathMapper {
  val pathMapper = getPathMapper(project, sdk, consoleSettings) ?: PyRemotePathMapper()
  for (volume in targetEnvironment.uploadVolumes.values) {
    pathMapper.addMapping(volume.localRoot.toString(), volume.targetRoot, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return pathMapper
}

@ApiStatus.Internal
fun getPathMapper(project: Project,
                  sdk: Sdk?,
                  consoleSettings: PyConsoleSettings): PyRemotePathMapper? {
  if (sdk == null) return null
  return when (val sdkAdditionalData = sdk.sdkAdditionalData) {
    is PyTargetAwareAdditionalData -> getPathMapper(project, consoleSettings, sdkAdditionalData)
    else -> null
  }
}

private fun getPathMapper(project: Project, consoleSettings: PyConsoleSettings, data: PyTargetAwareAdditionalData): PyRemotePathMapper {
  val remotePathMapper = appendBasicMappings(project, data)
  consoleSettings.mappingSettings.let { mappingSettings ->
    remotePathMapper.addAll(mappingSettings.pathMappings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return remotePathMapper
}

/**
 * Collects deployment paths from suitable mapping providers.
 *
 * If {@code data} is not a {@link PyTargetAwareAdditionalData}, then an empty set is returned.
 *
 * @param project the project for which deployment paths are being retrieved
 * @param data SDK additional data
 * @return a set of paths on remote file systems
 */
private fun getDeploymentPaths(project: Project, data: RemoteSdkProperties): Set<String> {
  val deploymentPaths = mutableSetOf<String>()
  if (data is PyTargetAwareAdditionalData) {
    for (provider in TargetAwarePathMappingProvider.getSuitableMappingProviders(data)) {
      for (pathMapping in provider.getPathMappingSettings(project, data).pathMappings) {
        deploymentPaths.add(pathMapping.remoteRoot)
      }
    }
  }
  return deploymentPaths
}

private fun appendBasicMappings(project: Project, data: RemoteSdkProperties): PyRemotePathMapper {
  val pathMapper = PyRemotePathMapper()
  PythonRemoteInterpreterManager.addHelpersMapping(data, pathMapper)

  // We don't want to resolve remote paths to sources copied from the remote when they're available locally
  val deploymentPaths = getDeploymentPaths(project, data)
  pathMapper.addAll(
    data.pathMappings.pathMappings.filter { it.remoteRoot !in deploymentPaths },
    PyRemotePathMapper.PyPathMappingType.SYS_PATH
  )

  val mappings = RemoteMappingsManager.getInstance(project).getForServer(PythonRemoteInterpreterManager.PYTHON_PREFIX, data.sdkId)
  if (mappings != null) {
    pathMapper.addAll(mappings.settings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return pathMapper
}

/**
 * The interpreter a console starts on, and the module it belongs to. Both `null` when the project offers neither.
 *
 * Two sources, in this order.
 *
 * 1. The Environment section of Settings | Build, Execution, Deployment | Console | Python Console. An interpreter
 *    pinned there, or "Use module SDK" naming a module, is the user's choice for every console in the project, so
 *    nothing below overrides it. The working directory beside it wins the same way, in `getWorkingDirFromSettings`.
 * 2. The interpreter of the subproject the console runs in — the one [getModuleToStartConsole] names when the caller
 *    named no module, which is the interpreter widget's own rule. One rule for both surfaces, so the console and the
 *    status bar never disagree.
 */
internal suspend fun findPythonSdkAndModule(project: Project, contextModule: Module?): Pair<Sdk?, Module?> {
  val settings = PyConsoleOptions.getInstance(project).pythonConsoleSettings
  val namedModule = settings.moduleName?.let { ModuleManager.getInstance(project).findModuleByName(it) }
  val module = when {
    // A pinned interpreter carries no module of its own, so the module only decides the working directory and the
    // tab title. The module named beside the pin is the user's word on both.
    settings.sdkHome != null -> namedModule ?: contextModule ?: getModuleToStartConsole(project)
    // "Use module SDK" says which module the interpreter comes from, and the caller's module outranks the named one:
    // it is the subproject the user acted on.
    settings.isUseModuleSdk -> contextModule ?: namedModule ?: getModuleToStartConsole(project)
    else -> contextModule ?: getModuleToStartConsole(project)
  }
  return Pair.create(findConsoleSdk(settings, module), module)
}

/**
 * The interpreter a console runs on: the one [settings] pin, and [module]'s own when they pin none.
 */
@ApiStatus.Internal
suspend fun findConsoleSdk(settings: PyConsoleSettings, module: Module?): Sdk? {
  @Suppress("DEPRECATION")
  settings.sdkHome?.let { PythonSdkUtil.findSdkByPath(it) }?.let { return it }
  return module?.findPythonSdk()
}

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

internal fun isInPydevConsole(element: PsiElement): Boolean {
  return element is PydevConsoleElement || getConsoleCommunication(element) != null || hasConsoleKey(element)
}

private fun hasConsoleKey(element: PsiElement): Boolean {
  val psiFile = element.containingFile ?: return false
  if (psiFile.virtualFile == null) return false
  val inConsole = element.containingFile.virtualFile.getUserData(PythonConsoleView.CONSOLE_KEY)
  return inConsole != null && inConsole
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

private fun getConsoleCommunication(element: PsiElement): ConsoleCommunication? {
  val containingFile = element.containingFile
  return containingFile?.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY)
}

internal fun getConsoleSdk(element: PsiElement): Sdk? {
  val containingFile = element.containingFile
  return containingFile?.getCopyableUserData(PydevConsoleRunner.CONSOLE_SDK)
}

@ApiStatus.Internal
suspend fun getModuleToStartConsole(project: Project): Module? {
  val selectedFile = readAction { FileEditorManager.getInstance(project).selectedFiles.firstOrNull() }
  return resolveConsoleTarget(project, selectedFile)?.module
}
