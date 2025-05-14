// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResultInfo
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.runtime.HatchConstants
import com.intellij.python.hatch.runtime.HatchRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import java.io.IOException
import java.nio.file.Path

/**
 * Handles hatch-specific errors, runs [transformer] only on outputs with codes 0 or 1 without tracebacks.
 */
private suspend fun <T> HatchRuntime.executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): PyExecResult<T> {
  val errorHandlerTransformer: ProcessOutputTransformer<T> = { output ->
    when {
      output.exitCode !in 0..1 -> Result.failure(null)
      output.exitCode == 1 && output.stdoutString.substringBefore('\n').contains("Traceback (most recent call last)") -> {
        val hatchErrorDescription = output.stdoutString.split('\n').lastOrNull { it.isNotEmpty() } ?: ""
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
  outputContentSupplier: (EelProcessExecutionResultInfo) -> String = { it.stdoutString },
  transformer: (MatchResult) -> Result<T, @NlsSafe String?>,
): PyExecResult<T> {
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

  protected suspend fun <T> executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): PyExecResult<T> {
    return runtime.executeAndHandleErrors(*command, *arguments, transformer = transformer)
  }

  protected suspend fun <T> executeAndMatch(vararg arguments: String, expectedOutput: Regex, transformer: (MatchResult) -> Result<T, @NlsSafe String?>): PyExecResult<T> {
    return runtime.executeAndMatch(*command, *arguments, expectedOutput = expectedOutput, transformer = transformer)
  }
}

class HatchCli(private val runtime: HatchRuntime) {
  /**
   * Build a project
   */
  fun build(): PyExecResult<Unit> = TODO()

  /**
   * Remove build artifacts
   */
  fun clean(): PyExecResult<Unit> = TODO()

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
  fun fmt(): PyExecResult<Unit> = TODO()

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
   *
   * @param[initExistingProject] Initialize an existing project
   */
  suspend fun new(projectName: String, location: Path? = null, initExistingProject: Boolean = false): PyResult<String> {
    val options = listOf(
      initExistingProject to "--init",
      true to projectName,
      (location != null) to location,
    ).makeOptions()
    return runtime.executeInteractive("new", *options) { eelProcess, _ ->
      if (initExistingProject) {
        try {
          eelProcess.sendWholeText("$projectName\n")
        } catch (error: IOException) {
          return@executeInteractive Result.failure("Failed to write to process: ${error.localizedMessage}")
        }
      }
      Result.success("Created")
    }
  }

  /**
   * View project information
   */
  fun project(): HatchProject = HatchProject(runtime)

  /**
   * Publish build artifacts
   */
  fun publish(): PyExecResult<Unit> = TODO()

  /**
   * Manage Python installations
   */
  fun python(): HatchPython = HatchPython(runtime)

  /**
   * Run commands within project environments
   */
  suspend fun run(envName: String? = null, vararg command: String): PyExecResult<String> {
    val envRuntime = envName?.let { runtime.withEnv(HatchConstants.AppEnvVars.ENV to it) } ?: runtime
    return envRuntime.executeAndHandleErrors("run", *command) { output ->
      if (output.exitCode != 0) return@executeAndHandleErrors Result.failure(null)

      val scenario = output.stderrString.trim()
      val installDetailsContent = output.stdoutString.replace("─", "").trim()
      val info = installDetailsContent.lines().drop(1).dropLast(2).joinToString("\n")

      Result.success("$scenario\n$info")
    }
  }

  /**
   * Manage Hatch
   */
  fun self(): HatchSelf = HatchSelf(runtime)

  /**
   * Enter a shell within a project's environment
   */
  fun shell(): PyExecResult<Unit> = TODO()

  data class HatchStatus(val project: String, val location: Path, val config: Path)

  /**
   * Show information about the current environment
   */
  suspend fun status(): PyExecResult<HatchStatus> {
    val expectedOutput = """^\[Project] - (.*)\n\[Location] - (.*)\n\[Config] - (.*)\n$""".toRegex()

    return runtime.executeAndMatch("status", expectedOutput = expectedOutput, outputContentSupplier = { it.stderrString }) { matchResult ->
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
  fun test(): PyExecResult<Unit> = TODO()

  /**
   * View a project's version.
   *
   * @return Project Version
   */
  suspend fun getVersion(): PyExecResult<Version> {
    return runtime.executeAndHandleErrors("version") { processOutput ->
      val output = processOutput.takeIf { it.exitCode == 0 }?.stdoutString?.trim()
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
  suspend fun setVersion(desiredVersion: String): PyResult<Pair<Version, Version>> {
    val expectedOutput = """^Old: (.*)\nNew: (.*)\n$""".toRegex()

    return runtime.executeAndMatch("version", desiredVersion, expectedOutput = expectedOutput, outputContentSupplier = { it.stderrString }) { matchResult ->
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


internal fun List<Pair<Boolean?, *>>.makeOptions(): Array<String> {
  return this.mapNotNull { (flag, option) ->
    when (flag) {
      true -> option.toString()
      else -> null
    }
  }.toTypedArray()
}