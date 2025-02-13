// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.WhatToExec
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import java.nio.file.Path

sealed class HatchError(message: @NlsSafe String) : PyError.Message(message)

class ExecutableNotFoundHatchError(path: Path?) : HatchError(
  PyHatchBundle.message("python.hatch.error.executable.is.not.found", path.toString())
)


fun EelApi.getHatchCommand(): String = when (platform) {
  is EelPlatform.Windows -> "hatch.exe"
  else -> "hatch"
}

suspend fun createHatchRuntime(
  workingDirectory: Path,
  envVars: Map<String, String> = emptyMap(),
  eelApi: EelApi = localEel,
): Result<HatchRuntime, HatchError> {
  val hatchExecutable = HatchConfiguration.getOrDetectHatchExecutablePath(eelApi).getOr { return it }

  val defaultVariables = mapOf(
    HatchConstants.AppEnvVars.NO_COLOR to "1",
    HatchConstants.AppEnvVars.VERBOSE to "1",
    HatchConstants.AppEnvVars.INTERACTIVE to "0",
    "TERM" to "dumb",
    "COLUMNS" to "${0x7FFF}",
    "LINES" to "${0x7FFF}",
  )
  val actualEnvVars = defaultVariables + envVars

  val runtime = HatchRuntime(
    hatchBinary = WhatToExec.Binary(hatchExecutable),
    execOptions = ExecOptions(
      env = actualEnvVars,
      workingDirectory = workingDirectory
    )
  )
  return Result.success(runtime)
}

