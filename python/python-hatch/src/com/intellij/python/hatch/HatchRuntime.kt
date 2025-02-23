// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch

import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.hatch.cli.HatchCli
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError.ExecException
import java.nio.file.Path


class HatchRuntime(
  val hatchBinary: WhatToExec,
  val execOptions: ExecOptions,
  private val execService: ExecService = ExecService(),
) {
  fun hatchCli(): HatchCli = HatchCli(this)

  fun withWorkingDirectory(workDirectoryPath: Path): HatchRuntime {
    val execOptions = with(this.execOptions) {
      ExecOptions(env, workDirectoryPath, processDescription, timeout)
    }
    return HatchRuntime(this.hatchBinary, execOptions)
  }

  fun withBasePythonPath(basePythonPath: Path): HatchRuntime {
    val execOptions = with(this.execOptions) {
      val modifiedEnv = env + mapOf(
        HatchConstants.AppEnvVars.PYTHON to basePythonPath.toString()
      )
      ExecOptions(modifiedEnv, workingDirectory, processDescription, timeout)
    }
    return HatchRuntime(this.hatchBinary, execOptions)
  }

  /**
   * Pure execution of [hatchBinary] with command line [arguments] and [execOptions] by [execService]
   * Doesn't make any validation of stdout/stderr content.
   */
  internal suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): Result<T, ExecException> {
    return execService.execute(hatchBinary, arguments.toList(), execOptions, processOutputTransformer)
  }
}

