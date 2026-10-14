// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.python.sdk.common.PyEnvRef
import java.nio.file.Path
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.project.project
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.findPyProject

/**
 * The interpreter of the environment of [manager] at [envRef], for the `PyProject` of [moduleOrProject]. It is not
 * registered yet: the caller sets it on the project. The provider of
 * [manager] builds the interpreter, as it does for the widget, so both surfaces create the same SDK.
 *
 * `null` when this path does not apply: a target file system, or no `PyProject` yet. The caller then builds the SDK
 * itself.
 */
internal suspend fun <P : PathHolder> FileSystem<P>.addInterpreterByEnvRef(
  moduleOrProject: ModuleOrProject,
  manager: PyTool,
  envRef: PyEnvRef,
): PyResult<PythonInterpreter>? {
  if (this !is FileSystemWithEel) return null
  val pyProject = moduleOrProject.findPyProject() ?: return null
  val provider = PyEvoEnvironmentProvider.EP_NAME.findFirstSafe { it.tool.fusId == manager.fusId } ?: return null
  return PythonInterpreterRegistry.getInstance(pyProject.project).newPythonInterpreter(pyProject, provider.interpreterRefOf(envRef))
}

/**
 * [addInterpreterByEnvRef] for the environment of [manager] whose Python binary is [pythonBinary]. The provider of
 * [manager] names the environment, see [PyEvoEnvironmentProvider.envRefOf].
 */
internal suspend fun <P : PathHolder> FileSystem<P>.addInterpreterByBinary(
  moduleOrProject: ModuleOrProject,
  manager: PyTool,
  pythonBinary: Path,
): PyResult<PythonInterpreter>? {
  if (this !is FileSystemWithEel) return null
  val pyProject = moduleOrProject.findPyProject() ?: return null
  val provider = PyEvoEnvironmentProvider.EP_NAME.findFirstSafe { it.tool.fusId == manager.fusId } ?: return null
  val ref = provider.interpreterRefOf(provider.envRefOf(pyProject, pythonBinary))
  return PythonInterpreterRegistry.getInstance(pyProject.project).newPythonInterpreter(pyProject, ref)
}
