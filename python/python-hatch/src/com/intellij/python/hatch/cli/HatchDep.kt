// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.pytools.backend.runtime.cliArg
import com.intellij.python.pytools.backend.runtime.cliArgs
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.PathHolder

enum class Scope(val options: Array<String>) {
  All(emptyArray()),
  Project(arrayOf("--project-only")),
  Env(arrayOf("--env-only")),
}

/**
 * Manage environment dependencies
 */
class HatchDep<P : PathHolder>(runtime: PyToolRuntime) : HatchCommand<P>("dep", runtime) {
  /**
   *  Output a hash of the currently defined dependencies
   **/
  suspend fun hash(scope: Scope = Scope.All): PyResult<String> {
    return executeAndHandleErrors("hash", *scope.options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Display dependencies in various formats
   */
  fun show(): HatchDepShow<P> = HatchDepShow(runtime)
}

/**
 * Manage environment dependencies
 */
class HatchDepShow<P : PathHolder>(runtime: PyToolRuntime) : HatchCommand<P>(arrayOf("dep", "show"), runtime) {
  /**
   * Enumerate dependencies as a list of requirements.
   *
   * @param features only show the dependencies of the specified features
   */
  suspend fun requirements(scope: Scope = Scope.All, features: List<String>? = null): PyResult<String> {
    val arguments = cliArgs(
      scope.options.asList(),
      features?.flatMap { listOf("--feature", it) } ?: listOf("--all"),
    )
    return executeAndHandleErrors("requirements", *arguments, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Enumerate dependencies in a tabular format.
   */
  suspend fun table(scope: Scope = Scope.All): PyResult<String> {
    val arguments = cliArgs(
      scope.options.asList(),
      cliArg("--ascii"),
    )
    return executeAndHandleErrors("table", *arguments, transformer = ZeroCodeStdoutTransformer)
  }
}
