// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.hatch.runtime.HatchRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError

enum class Scope(val options: Array<String>) {
  All(emptyArray()),
  Project(arrayOf("--project-only")),
  Env(arrayOf("--env-only")),
}

/**
 * Manage environment dependencies
 */
class HatchDep(runtime: HatchRuntime) : HatchCommand("dep", runtime) {
  /**
   *  Output a hash of the currently defined dependencies
   **/
  suspend fun hash(scope: Scope = Scope.All): Result<String, ExecError> {
    return executeAndHandleErrors("hash", *scope.options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Display dependencies in various formats
   */
  fun show(): HatchDepShow = HatchDepShow(runtime)
}

/**
 * Manage environment dependencies
 */
class HatchDepShow(runtime: HatchRuntime) : HatchCommand(arrayOf("dep", "show"), runtime) {
  /**
   * Enumerate dependencies as a list of requirements.
   *
   * @param features only show the dependencies of the specified features
   */
  suspend fun requirements(scope: Scope = Scope.All, features: List<String>? = null): Result<String, ExecError> {
    val options = features?.flatMap { listOf("--feature", it) }?.toTypedArray() ?: arrayOf("--all")
    return executeAndHandleErrors("requirements", *scope.options, *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Enumerate dependencies in a tabular format.
   */
  suspend fun table(scope: Scope = Scope.All): Result<String, ExecError> {
    val options = listOf(null to "--lines", true to "--ascii").makeOptions()
    return executeAndHandleErrors("table", *scope.options, *options, transformer = ZeroCodeStdoutTransformer)
  }
}
