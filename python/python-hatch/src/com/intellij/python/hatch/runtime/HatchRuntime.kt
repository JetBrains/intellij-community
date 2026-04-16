// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.runtime

import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.cli.HatchCli
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.pytools.runtime.WorkingDirectoryNotFoundError
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.errorProcessing.PyError
import java.nio.file.Path
import kotlin.io.path.isDirectory

fun PyToolRuntime.hatchCli(): HatchCli = HatchCli(this)

suspend fun createHatchRuntime(
  fileSystem: FileSystem<PathHolder.Eel>,
  hatchExecutablePath: Path?,
  workingDirectoryPath: Path?,
  envVars: Map<String, String> = emptyMap(),
): Result<PyToolRuntime, PyError> {
  val actualHatchExecutable = hatchExecutablePath
                              ?: HatchConfiguration.getOrDetectHatchExecutablePath(fileSystem).getOr { return it }.path
  if (workingDirectoryPath?.isDirectory() != true) {
    return Result.failure(WorkingDirectoryNotFoundError(workingDirectoryPath))
  }

  val defaultVariables = mapOf(
    HatchConstants.AppEnvVars.NO_COLOR to "1",
    HatchConstants.AppEnvVars.VERBOSE to "1",
    HatchConstants.AppEnvVars.INTERACTIVE to "0",
    "TERM" to "dumb",
    "COLUMNS" to "${0x7FFF}",
    "LINES" to "${0x7FFF}",
  )
  val actualEnvVars = defaultVariables + envVars

  val runtime = PyToolRuntime(
    binary = BinOnEel(actualHatchExecutable, workingDirectoryPath),
    execOptions = ExecOptions(
      env = actualEnvVars,
    )
  )
  return Result.success(runtime)
}
