// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Returns the string representation of the Python executable ("py" on Windows or "python" on Unix-like OS) based on the current system.
 *
 * @return the string representation of the Python executable
 */
@Internal
fun getPythonExecutableString(): String = if (SystemInfo.isWindows) "py" else "python"

/**
 * Installs an executable via a Python script.
 *
 * @param [scriptPath] The [Path] to the Python script used for installation.
 * @param [pythonExecutable] The path to the Python executable (could be "py" or "python").
 *
 * @return executable [Path]
 */
@Internal
suspend fun installExecutableViaPythonScript(scriptPath: Path, pythonExecutable: PythonBinary, vararg args: String): PyResult<Path> {
  val eel = pythonExecutable.getEelDescriptor().upgrade()
  val scriptPath = EelPathUtils.transferLocalContentToRemote(scriptPath, EelPathUtils.TransferTarget.Temporary(eel.descriptor)).asEelPath()
  val result = ExecService().execGetStdout(WhatToExec.Binary(pythonExecutable), listOf(scriptPath.toString()) + args.toList()).getOr { return it }
  return Result.success(EelPath.parse(result.trim().split("\n").last(), eel.descriptor).asNioPath())
}