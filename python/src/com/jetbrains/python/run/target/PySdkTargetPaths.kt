// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PySdkTargetPaths")

package com.jetbrains.python.run.target

import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.createMapper
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.remote.PyRemotePathMapper
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The function that resolves [localPath] to the path on the target: by [pathMapper], the path mappings of the run
 * configuration, else by the uploads that the target environment declares.
 *
 * @throws IllegalArgumentException if [localPath] is in no mapping and in no upload of the request
 */
@ApiStatus.Internal
internal fun getTargetPathForPythonScriptExecution(pathMapper: PyRemotePathMapper?, localPath: Path): TargetEnvironmentFunction<String> {
  val targetPath = pathMapper?.convertToRemoteOrNull(localPath)
  return targetPath?.let { constant(it) } ?: targetPath(localPath)
}

/**
 * Returns the function that resolves the given [localPath] to the path on the target by the target environment. The resolution happens in
 * the following order:
 * 1. Using the given [pathMapper]. This mapper usually encapsulates the path mappings declared by user in the run configuration.
 * 2. Using the project-wide path mappings settings for Python Console.
 * 3. Using the path mappings of the given [interpreter] including the mappings for PyCharm helpers.
 * 4. Using the uploads declared in the target environment.
 *
 * @param pathMapper corresponds to the path mappings specified in the run configuration
 * @throws IllegalArgumentException if [localPath] cannot be found neither in SDK additional data nor within the registered uploads in the
 *                                  request
 */
internal fun getTargetPathForPythonConsoleExecution(project: Project,
                                           interpreter: PythonInterpreter?,
                                           pathMapper: PyRemotePathMapper?,
                                           localPath: Path): TargetEnvironmentFunction<String> {
  val targetPath = pathMapper?.convertToRemoteOrNull(localPath)
                   ?: interpreter?.let { getPythonConsolePathMapper(project, it).convertToRemoteOrNull(localPath) }
  return targetPath?.let { constant(it) } ?: targetPath(localPath)
}

/**
 * Note that the returned mapper includes the path mappings collected by the execution of [appendBasicMappings].
 */
@RequiresBackgroundThread
private fun getPythonConsolePathMapper(project: Project, interpreter: PythonInterpreter): PyRemotePathMapper =
  runBlockingMaybeCancellable {
    interpreter.createMapper(PyConsoleOptions.getInstance(project).pythonConsoleSettings.mappingSettings)
  }

private fun PyRemotePathMapper.convertToRemoteOrNull(localPath: Path): String? =
  takeIf { it.canReplaceLocal(localPath.toString()) }?.convertToRemote(localPath.toString())
