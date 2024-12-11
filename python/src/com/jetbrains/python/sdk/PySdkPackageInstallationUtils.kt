// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Returns the string representation of the Python executable ("py" on Windows or "python" on Unix-like OS) based on the current system.
 *
 * @return the string representation of the Python executable
 */
@Internal
fun getPythonExecutableString() = if (SystemInfo.isWindows) "py" else "python"

/**
 * Installs an executable via a Python script.
 *
 * @param [scriptPath] The [Path] to the Python script used for installation.
 * @param [pythonExecutable] The path to the Python executable (could be "py" or "python").
 *
 * @return executable [Path]
 */
@Internal
suspend fun installExecutableViaPythonScript(scriptPath: Path, pythonExecutable: String, vararg args: String): Result<Path> {
  val result = runCommandLine(GeneralCommandLine(pythonExecutable, scriptPath.absolutePathString(), *args)).getOrElse { return Result.failure(it) }
  return Result.success(Path.of(result.split("\n").last()))
}