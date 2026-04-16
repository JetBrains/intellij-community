// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.pytools.runtime.executeAndHandleErrors
import com.intellij.python.pytools.runtime.executeAndMatch
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult

sealed class UvCommand(private val command: Array<String>, protected val runtime: PyToolRuntime) {
  @Suppress("unused")
  constructor(command: String, runtime: PyToolRuntime) : this(arrayOf(command), runtime)

  protected suspend fun <T> executeAndHandleErrors(vararg arguments: String, transformer: ProcessOutputTransformer<T>): PyResult<T> {
    return runtime.executeAndHandleErrors(*command, *arguments, transformer = transformer)
  }

  @Suppress("unused")
  protected suspend fun <T> executeAndMatch(vararg arguments: String, expectedOutput: Regex, transformer: (MatchResult) -> Result<T, @NlsSafe String?>): PyResult<T> {
    return runtime.executeAndMatch(*command, *arguments, expectedOutput = expectedOutput, transformer = transformer)
  }
}

@Suppress("unused")
class UvCli(private val runtime: PyToolRuntime) {
  /**
   * Manage authentication
   */
  fun auth(): UvAuth = UvAuth(runtime)

  /**
   * Run a command or script
   */
  suspend fun run(): PyResult<Unit> = TODO()

  /**
   * Create a new project.
   *
   * @param name Name or path of the new project. When a relative path is passed, uv creates the
   *   project in a subdirectory with that name under the working directory.
   */
  suspend fun init(name: String? = null): PyResult<String> {
    val arguments = if (name == null) emptyArray() else arrayOf(name)
    return runtime.executeAndHandleErrors("init", *arguments, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Add dependencies to the project
   */
  suspend fun add(): PyResult<Unit> = TODO()

  /**
   * Remove dependencies from the project
   */
  suspend fun remove(): PyResult<Unit> = TODO()

  /**
   * Read or update the project's version
   */
  suspend fun getVersion(): PyResult<String> {
    return runtime.executeAndHandleErrors("version", "--short", transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Set the project's version.
   */
  suspend fun setVersion(): PyResult<Unit> = TODO()

  /**
   * Update the project's environment
   */
  suspend fun sync(frozen: Boolean? = null, locked: Boolean? = null): PyResult<String> {
    val options = listOf(frozen to "--frozen", locked to "--locked").makeOptions()
    return runtime.executeAndHandleErrors("sync", *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Update the project's lockfile
   */
  suspend fun lock(): PyResult<Unit> = TODO()

  /**
   * Export the project's lockfile to an alternate format
   */
  suspend fun export(): PyResult<Unit> = TODO()

  /**
   * Display the project's dependency tree
   */
  suspend fun tree(): PyResult<Unit> = TODO()

  /**
   * Format Python code in the project
   */
  suspend fun format(): PyResult<Unit> = TODO()

  /**
   * Run and install commands provided by Python packages
   */
  fun tool(): UvTool = UvTool(runtime)

  /**
   * Manage Python versions and installations
   */
  fun python(): UvPython = UvPython(runtime)

  /**
   * Manage Python packages with a pip-compatible interface
   */
  fun pip(): UvPip = UvPip(runtime)

  /**
   * Create a virtual environment
   */
  suspend fun venv(): PyResult<Unit> = TODO()

  /**
   * Build Python packages into source distributions and wheels
   */
  suspend fun build(): PyResult<Unit> = TODO()

  /**
   * Upload distributions to an index
   */
  suspend fun publish(): PyResult<Unit> = TODO()

  /**
   * Manage uv's cache
   */
  fun cache(): UvCache = UvCache(runtime)

  /**
   * Manage the uv executable
   */
  fun self(): UvSelf = UvSelf(runtime)

  /**
   * Display documentation for a command
   */
  suspend fun help(vararg command: String): PyResult<String> {
    return runtime.executeAndHandleErrors("help", *command, transformer = ZeroCodeStdoutTransformer)
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
