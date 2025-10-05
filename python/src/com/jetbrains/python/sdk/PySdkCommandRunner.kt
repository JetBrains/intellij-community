// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.community.execService.*
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Internal
suspend fun runExecutableWithProgress(
  executable: Path, workDir: Path?,
  timeout: Duration = 10.minutes,
  env: Map<String, String> = emptyMap(),
  vararg args: String,
): PyResult<String> {
  return runExecutableWithProgress(executable, workDir, timeout, env, *args, transformer = ZeroCodeStdoutTransformer)
}


/**
 * Executes a given executable with specified arguments within an optional project directory.
 * Progress is reported as `text` of the current progress (if any)
 *
 * @param [executable] The [Path] to the executable to run.
 * @param [workDir] The path to the project directory in which to run the executable, or null if no specific directory is required.
 * @param [args] The arguments to pass to the executable.
 * @return A [Result] object containing the output of the command execution.
 */
@Internal
suspend fun <T> runExecutableWithProgress(
  executable: Path, workDir: Path?,
  timeout: Duration = 10.minutes,
  env: Map<String, String> = emptyMap(),
  vararg args: String,
  transformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  val execOptions = ExecOptions(timeout = timeout, env = env)

  val errorHandlerTransformer: ProcessOutputTransformer<T> = { output ->
    when {
      output.exitCode == 0 -> transformer.invoke(output)
      else -> Result.failure(null)
    }
  }

  return ExecService().execute(
    binary = BinOnEel(executable, workDir),
    args = Args(*args),
    options = execOptions,
    processOutputTransformer = errorHandlerTransformer
  )
}
