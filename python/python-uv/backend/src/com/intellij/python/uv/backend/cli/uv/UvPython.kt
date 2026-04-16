// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage Python versions and installations
 */
@Suppress("unused")
class UvPython(runtime: PyToolRuntime) : UvCommand("python", runtime) {
  /**
   * List the available Python installations
   */
  suspend fun list(onlyInstalled: Boolean? = null): PyResult<String> {
    val options = listOf(onlyInstalled to "--only-installed").makeOptions()
    return executeAndHandleErrors("list", *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Download and install Python versions
   */
  suspend fun install(): PyResult<Unit> = TODO()

  /**
   * Upgrade installed Python versions
   */
  suspend fun upgrade(): PyResult<Unit> = TODO()

  /**
   * Search for a Python installation
   */
  suspend fun find(): PyResult<String> = TODO()

  /**
   * Pin to a specific Python version
   */
  suspend fun pin(): PyResult<String> = TODO()

  /**
   * Show the uv Python installation directory
   */
  suspend fun dir(): PyResult<String> {
    return executeAndHandleErrors("dir", transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Uninstall Python versions
   */
  suspend fun uninstall(): PyResult<Unit> = TODO()

  /**
   * Ensure that the Python executable directory is on the PATH
   */
  suspend fun updateShell(): PyResult<Unit> = TODO()
}
