// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.runtime

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.processSemiInteractiveHandler
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path

/**
 * Runtime for invoking a CLI tool (hatch, uv, ...).
 * Holds the tool [binary], the [execOptions] used to invoke it, and an [execService] that runs the process.
 *
 * Tool-specific factories (such as `createHatchRuntime`) construct instances and add any validation around inputs.
 */
data class PyToolRuntime(
  val binary: BinOnEel,
  val execOptions: ExecOptions,
  private val execService: ExecService = ExecService(),
) {
  fun withEnv(vararg envVars: Pair<String, String>): PyToolRuntime =
    copy(execOptions = execOptions.copy(env = execOptions.env + envVars))

  fun withWorkingDirectory(workDirectoryPath: Path): PyToolRuntime =
    copy(binary = binary.copy(workDir = workDirectoryPath))

  /**
   * Points the tool at a specific base Python by setting [pythonEnvVar] to [basePythonPath].
   * The env-var name is tool-specific (e.g. `HATCH_PYTHON` for hatch, `UV_PYTHON` for uv).
   */
  fun withBasePythonBinaryPath(basePythonPath: PythonBinary, pythonEnvVar: String): PyToolRuntime =
    withEnv(pythonEnvVar to basePythonPath.asEelPath().toString())

  /**
   * Pure execution of [binary] with command-line [arguments] and [execOptions] via [execService].
   * Does not validate stdout/stderr content.
   */
  suspend fun <T> execute(vararg arguments: String, processOutputTransformer: ProcessOutputTransformer<T>): PyResult<T> =
    execService.execute(binary, Args(*arguments), execOptions, processOutputTransformer = processOutputTransformer)

  suspend fun <T> executeInteractive(vararg arguments: String, processSemiInteractiveFun: ProcessSemiInteractiveFun<T>): PyResult<T> =
    execService.executeAdvanced(binary, Args(*arguments), execOptions, processSemiInteractiveHandler(code = processSemiInteractiveFun))
}
