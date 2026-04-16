// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.runtime

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResultInfo
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.pytools.PyToolsBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Runs [transformer] only on outputs with exit codes 0 or 1.
 * Treats exit code 1 with a Python traceback on stdout as a tool failure and extracts the last non-empty
 * line of stdout as the error description; other exit codes outside 0..1 fail with no description.
 */
suspend fun <T> PyToolRuntime.executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): PyResult<T> {
  val errorHandlerTransformer: ProcessOutputTransformer<T> = { output ->
    when (output.exitCode) {
      !in 0..1 -> Result.failure(null)
      1 if output.stdoutString.substringBefore('\n').contains("Traceback (most recent call last)") -> {
        val errorDescription = output.stdoutString.split('\n').lastOrNull { it.isNotEmpty() } ?: ""
        Result.failure(errorDescription)
      }
      else -> transformer.invoke(output)
    }
  }

  return this.execute(*arguments, processOutputTransformer = errorHandlerTransformer)
}

/**
 * Runs the tool, validates a successful exit code, and matches the resulting output content against
 * [expectedOutput]. On a match, delegates to [transformer]; otherwise fails with a localized
 * "out of pattern" message.
 */
suspend fun <T> PyToolRuntime.executeAndMatch(
  vararg arguments: String,
  expectedOutput: Regex,
  outputContentSupplier: (EelProcessExecutionResultInfo) -> String = { it.stdoutString },
  transformer: (MatchResult) -> Result<T, @NlsSafe String?>,
): PyResult<T> {
  return this.executeAndHandleErrors(*arguments) { processOutput ->
    if (processOutput.exitCode != 0) return@executeAndHandleErrors Result.failure(null)
    val output = outputContentSupplier.invoke(processOutput).let { Strings.convertLineSeparators(it) }
    val matchResult = expectedOutput.matchEntire(output)
    if (matchResult == null) {
      Result.failure(PyToolsBundle.message("python.tool.cli.error.response.out.of.pattern", expectedOutput.toString()))
    }
    else {
      transformer.invoke(matchResult)
    }
  }
}
