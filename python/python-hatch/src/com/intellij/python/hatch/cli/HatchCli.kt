// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.hatch.HatchRuntime
import com.intellij.python.hatch.PyHatchBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyError.ExecException
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import java.nio.file.Path

/**
 * Handles hatch-specific errors, runs [transformer] only on outputs with codes 0 or 1 without tracebacks.
 */
private suspend fun <T> HatchRuntime.executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): Result<T, ExecException> {
  val errorHandlerTransformer: ProcessOutputTransformer<T> = { output ->
    when {
      output.exitCode !in 0..1 -> Result.failure(null)
      output.exitCode == 1 && output.stdout.substringBefore('\n').contains("Traceback (most recent call last)") -> {
        val hatchErrorDescription = output.stdout.split('\n').lastOrNull { it.isNotEmpty() } ?: ""
        Result.failure(hatchErrorDescription)
      }
      else -> transformer.invoke(output)
    }
  }

  return this.execute(*arguments, processOutputTransformer = errorHandlerTransformer)
}

private suspend fun <T> HatchRuntime.executeAndMatch(
  vararg arguments: String,
  expectedOutput: Regex,
  outputContentSupplier: (ProcessOutput) -> String = ProcessOutput::getStdout,
  transformer: (MatchResult) -> Result<T, @NlsSafe String?>,
): Result<T, ExecException> {
  return this.executeAndHandleErrors(*arguments) { processOutput ->
    if (processOutput.exitCode != 0) return@executeAndHandleErrors Result.failure(null)

    val output = outputContentSupplier.invoke(processOutput).replace("\r\n", "\n")
    val matchResult = expectedOutput.matchEntire(output)
    if (matchResult == null) {
      Result.failure(PyHatchBundle.message("python.hatch.cli.error.response.out.of.pattern", expectedOutput.toString()))
    }
    else {
      transformer.invoke(matchResult)
    }
  }
}

sealed class HatchCommand(private val command: Array<String>, protected val runtime: HatchRuntime) {
  @Suppress("unused")
  constructor(command: String, runtime: HatchRuntime) : this(arrayOf(command), runtime)

  protected suspend fun <T> executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): Result<T, ExecException> {
    return runtime.executeAndHandleErrors(*command, *arguments, transformer = transformer)
  }

  protected suspend fun <T> executeAndMatch(vararg arguments: String, expectedOutput: Regex, transformer: (MatchResult) -> Result<T, @NlsSafe String?>): Result<T, ExecException> {
    return runtime.executeAndMatch(*command, *arguments, expectedOutput = expectedOutput, transformer = transformer)
  }
}

class HatchCli(private val runtime: HatchRuntime) {
  /**
   * Build a project
   */
  fun build(): Result<Unit, ExecException> = TODO()

  /**
   * Remove build artifacts
   */
  fun clean(): Result<Unit, ExecException> = TODO()

  /**
   * Manage the config file
   */
  fun config(): HatchConfig = HatchConfig(runtime)

  /**
   * Manage environment dependencies
   */
  fun dep(): HatchDep = HatchDep(runtime)

  /**
   * Manage project environments
   */
  fun env(): HatchEnv = HatchEnv(runtime)

  /**
   * Format and lint source code
   */
  fun fmt(): Result<Unit, ExecException> = TODO()

  /**
   * Create or initialize a project.
   * Returns projects tree structure in human-readable ascii view:
   *
   * {projectName}
   * ├── src
   * │   └── {projectName}
   * │       ├── __about__.py
   * │       └── __init__.py
   * ├── tests
   * │   └── __init__.py
   * ├── LICENSE.txt
   * ├── README.md
   * └── pyproject.toml
   */
  suspend fun new(projectName: String): Result<String, PyError> {
    return runtime.executeAndHandleErrors("new", projectName, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * View project information
   */
  fun project(): HatchProject = HatchProject(runtime)

  /**
   * Publish build artifacts
   */
  fun publish(): Result<Unit, ExecException> = TODO()

  /**
   * Manage Python installations
   */
  fun python(): HatchPython = HatchPython(runtime)

  /**
   * Run commands within project environments
   */
  fun run(): Result<Unit, ExecException> = TODO()

  /**
   * Manage Hatch
   */
  fun self(): HatchSelf = HatchSelf(runtime)

  /**
   * Enter a shell within a project's environment
   */
  fun shell(): Result<Unit, ExecException> = TODO()

  data class HatchStatus(val project: String, val location: Path, val config: Path)

  /**
   * Show information about the current environment
   */
  suspend fun status(): Result<HatchStatus, ExecException> {
    val expectedOutput = """^\[Project] - (.*)\n\[Location] - (.*)\n\[Config] - (.*)\n$""".toRegex()

    return runtime.executeAndMatch("status", expectedOutput = expectedOutput, outputContentSupplier = { it.stderr }) { matchResult ->
      val (project, location, config) = matchResult.destructured
      try {
        Result.success(HatchStatus(project, Path.of(location), Path.of(config)))
      }
      catch (e: Exception) {
        Result.failure(e.localizedMessage)
      }
    }
  }

  /**
   * Run tests
   */
  fun test(): Result<Unit, ExecException> = TODO()

  /**
   * View a project's version.
   *
   * @return Project Version
   */
  suspend fun getVersion(): Result<Version, ExecException> {
    return runtime.executeAndHandleErrors("version") { processOutput ->
      val output = processOutput.takeIf { it.exitCode == 0 }?.stdout?.trim()
                   ?: return@executeAndHandleErrors Result.failure(null)
      try {
        Result.success(Version.parse(output))
      }
      catch (e: VersionFormatException) {
        Result.failure(e.localizedMessage)
      }
    }
  }

  /**
   * Set a project's version.
   *
   * @return OldVersion to NewVersion as Pair
   */
  suspend fun setVersion(desiredVersion: String): Result<Pair<Version, Version>, PyError> {
    val expectedOutput = """^Old: (.*)\nNew: (.*)\n$""".toRegex()

    return runtime.executeAndMatch("version", desiredVersion, expectedOutput = expectedOutput, outputContentSupplier = { it.stderr }) { matchResult ->
      val (oldVersion, newVersion) = matchResult.destructured
      try {
        Result.success(Version.parse(oldVersion) to Version.parse(newVersion))
      }
      catch (e: VersionFormatException) {
        Result.failure(e.localizedMessage)
      }
    }
  }
}


internal fun List<Pair<Boolean?, String?>>.makeOptions(): Array<String> {
  return this.mapNotNull { (flag, option) ->
    when (flag) {
      true -> option
      false -> "$option=0"
      null -> null
    }
  }.toTypedArray()
}