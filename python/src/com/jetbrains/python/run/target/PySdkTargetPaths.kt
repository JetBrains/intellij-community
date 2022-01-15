// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PySdkTargetPaths")

package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getTargetEnvironmentValueForLocalPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteMappingsManager
import com.intellij.remote.RemoteSdkAdditionalData
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PyConsoleOptions.PyConsoleSettings
import com.jetbrains.python.console.PydevConsoleRunner
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.remote.PythonRemoteInterpreterManager.appendBasicMappings
import com.jetbrains.python.target.PyTargetAwareAdditionalData

/**
 * @param pathMapper corresponds to the path mappings specified in the run configuration
 * @throws IllegalArgumentException if [localPath] cannot be found neither in SDK additional data nor within the registered uploads in the
 *                                  request
 */
fun getTargetPathForPythonScriptExecution(targetEnvironmentRequest: TargetEnvironmentRequest,
                                          project: Project,
                                          sdk: Sdk?,
                                          pathMapper: PyRemotePathMapper?,
                                          localPath: String): TargetEnvironmentFunction<String> {
  val initialPathMapper = pathMapper ?: PyRemotePathMapper()
  val targetPath = initialPathMapper.extendPythonSdkPathMapper(project, sdk).convertToRemoteOrNull(localPath)
  return targetPath?.let { constant(it) } ?: targetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath)
}

private fun PyRemotePathMapper.extendPythonSdkPathMapper(project: Project, sdk: Sdk?): PyRemotePathMapper {
  val pathMapper = PyRemotePathMapper.cloneMapper(this)
  val sdkAdditionalData = sdk?.sdkAdditionalData as? RemoteSdkAdditionalData<*>
  if (sdkAdditionalData != null) {
    appendBasicMappings(project, pathMapper, sdkAdditionalData)
  }
  return pathMapper
}

/**
 * Returns the function that resolves the given [localPath] to the path on the target by the target environment. The resolution happens in
 * the following order:
 * 1. Using the given [pathMapper]. This mapper usually encapsulates the path mappings declared by user in the run configuration.
 * 2. Using the project-wide path mappings settings for Python Console.
 * 3. Using the path mappings declared in the given [sdk] including the mappings for PyCharm helpers.
 * 4. Using the uploads declared in the target environment.
 *
 * @param pathMapper corresponds to the path mappings specified in the run configuration
 * @throws IllegalArgumentException if [localPath] cannot be found neither in SDK additional data nor within the registered uploads in the
 *                                  request
 */
fun getTargetPathForPythonConsoleExecution(targetEnvironmentRequest: TargetEnvironmentRequest,
                                           project: Project,
                                           sdk: Sdk?,
                                           pathMapper: PyRemotePathMapper?,
                                           localPath: String): TargetEnvironmentFunction<String> {
  val targetPath = pathMapper?.convertToRemoteOrNull(localPath)
                   ?: getPythonConsolePathMapper(project, sdk)?.convertToRemoteOrNull(localPath)
  return targetPath?.let { constant(it) } ?: targetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath)
}

/**
 * Note that the returned mapper includes the path mappings collected by the execution of [appendBasicMappings].
 */
private fun getPythonConsolePathMapper(project: Project, sdk: Sdk?): PyRemotePathMapper? =
  PydevConsoleRunner.getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).pythonConsoleSettings)

private fun PyRemotePathMapper.convertToRemoteOrNull(localPath: String): String? =
  takeIf { it.canReplaceLocal(localPath) }?.convertToRemote(localPath)

fun getPathMapper(project: Project, consoleSettings: PyConsoleSettings, data: PyTargetAwareAdditionalData): PyRemotePathMapper {
  val remotePathMapper = appendBasicMappings(project, null, data)
  consoleSettings.mappingSettings?.let { mappingSettings ->
    remotePathMapper.addAll(mappingSettings.pathMappings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return remotePathMapper
}

private fun appendBasicMappings(project: Project?,
                                pathMapper: PyRemotePathMapper?,
                                data: PyTargetAwareAdditionalData): PyRemotePathMapper {
  val newPathMapper = PyRemotePathMapper.cloneMapper(pathMapper)
  PythonRemoteInterpreterManager.addHelpersMapping(data, newPathMapper)
  newPathMapper.addAll(data.pathMappings.pathMappings, PyRemotePathMapper.PyPathMappingType.SYS_PATH)
  if (project != null) {
    val mappings = RemoteMappingsManager.getInstance(project).getForServer(PythonRemoteInterpreterManager.PYTHON_PREFIX, data.sdkId)
    if (mappings != null) {
      newPathMapper.addAll(mappings.settings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
    }
  }
  return newPathMapper
}