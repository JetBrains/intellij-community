// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend.runtime

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResultInfo
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.pytools.backend.PyToolsBundle
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

// Builders for the arguments of a tool's command line. Each returns the tokens one argument contributes — none when
// the caller left it unset — and `cliArgs` concatenates them into the array the runtime takes, so that every
// parameter of a command occupies one line of the call, in the order the command's signature declares it.

/**
 * One token, passed when [value] is not null — a switch, `cliArg("--bare".takeIf { bare })`, or a positional value,
 * `cliArg(projectName)`.
 *
 * Taking the token rather than a name and a boolean keeps one builder for every switch, including a set of mutually
 * exclusive ones modelled as an enum of flags — `cliArg(kind?.flag)` — where "which one" and "whether any" are one
 * question.
 */
fun cliArg(value: Any?): List<String> = listOfNotNull(value?.toString())

/** An option and its value, passed together when [value] is not null, and both dropped when it is. */
fun cliOption(name: String, value: Any?): List<String> = if (value == null) emptyList() else listOf(name, value.toString())

/**
 * Renders [cliArgs] into the token array the runtime takes.
 *
 * An argument the caller left unset contributes nothing, which is not the same as naming the tool's default: those
 * are the tool's own and can change between releases — `uv init` creates a git repository when `--vcs` is absent — so
 * a caller that must have the other behaviour passes the opposing value rather than leaving the argument out.
 */
fun cliArgs(vararg cliArgs: List<String>): Array<String> = cliArgs.flatMap { it }.toTypedArray()
