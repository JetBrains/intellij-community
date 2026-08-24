// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.sdk.backend.executeTool
import com.intellij.python.sdk.backend.executeToolInteractive
import com.intellij.python.sdk.backend.findToolExecutable
import com.intellij.python.sdk.backend.resolveToolVersion
import com.intellij.python.sdk.backend.toolExecutableWithBaseArgs
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonInterpreter
import java.nio.file.Path

/*
 * Deprecated proxies. The logic now lives in `com.intellij.python.sdk.backend`, as extensions on `ModuleOrProject` and
 * `PythonInterpreter` — the receivers that decide where a tool is found and how it runs — rather than on the tool. These
 * forward and exist only so the existing call sites keep compiling; new code should call those extensions directly.
 */

@Deprecated(
  "Use ModuleOrProject.toolExecutableWithBaseArgs instead",
  ReplaceWith(
    "moduleOrProject.toolExecutableWithBaseArgs(this, executableName, workingDir)",
    "com.intellij.python.sdk.backend.toolExecutableWithBaseArgs",
  ),
)
suspend fun PyTool.getExecutableWithBaseArgs(
  moduleOrProject: ModuleOrProject,
  executableName: String = packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> = moduleOrProject.toolExecutableWithBaseArgs(this, executableName, workingDir)

@Deprecated(
  "Use ModuleOrProject.executeTool instead",
  ReplaceWith("moduleOrProject.executeTool(this, args, execOptions)", "com.intellij.python.sdk.backend.executeTool"),
)
suspend fun PyTool.executeOn(
  moduleOrProject: ModuleOrProject,
  args: Args,
  execOptions: ExecOptions = ExecOptions(),
): PyResult<String> = moduleOrProject.executeTool(this, args, execOptions)

@Deprecated(
  "Use ModuleOrProject.executeToolInteractive instead",
  ReplaceWith(
    "moduleOrProject.executeToolInteractive(this, args, workingDir, execOptions, processSemiInteractiveFun)",
    "com.intellij.python.sdk.backend.executeToolInteractive",
  ),
)
suspend fun <T> PyTool.executeInteractiveOn(
  moduleOrProject: ModuleOrProject,
  args: Args,
  workingDir: Path? = null,
  execOptions: ExecOptions = ExecOptions(),
  processSemiInteractiveFun: ProcessSemiInteractiveFun<T>,
): PyResult<T> = moduleOrProject.executeToolInteractive(this, args, workingDir, execOptions, processSemiInteractiveFun)

@Deprecated(
  "Use ModuleOrProject.resolveToolVersion instead",
  ReplaceWith("moduleOrProject.resolveToolVersion(this)", "com.intellij.python.sdk.backend.resolveToolVersion"),
)
suspend fun PyTool.resolveVersion(moduleOrProject: ModuleOrProject): PyResult<Version> =
  moduleOrProject.resolveToolVersion(this)

@Deprecated(
  "Use PythonInterpreter.findToolExecutable instead",
  ReplaceWith(
    "pythonInterpreter.findToolExecutable(this, executableName)",
    "com.intellij.python.sdk.backend.findToolExecutable",
  ),
)
fun PyTool.findExecutableInSdk(pythonInterpreter: PythonInterpreter, executableName: String = packageName.name): Path? =
  pythonInterpreter.findToolExecutable(this, executableName)
