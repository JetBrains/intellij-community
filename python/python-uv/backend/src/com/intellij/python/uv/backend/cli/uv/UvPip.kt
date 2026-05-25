// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage Python packages with a pip-compatible interface
 */
@Suppress("unused")
class UvPip(runtime: PyToolRuntime) : UvCommand("pip", runtime) {
  /**
   * Compile a requirements.in file to a requirements.txt or pylock.toml file
   */
  suspend fun compile(): PyResult<Unit> = TODO()

  /**
   * Sync an environment with a requirements.txt or pylock.toml file
   */
  suspend fun sync(): PyResult<Unit> = TODO()

  /**
   * Install packages into an environment
   */
  suspend fun install(): PyResult<Unit> = TODO()

  /**
   * Uninstall packages from an environment
   */
  suspend fun uninstall(): PyResult<Unit> = TODO()

  /**
   * List, in requirements format, packages installed in an environment
   */
  suspend fun freeze(): PyResult<String> = TODO()

  /**
   * List, in tabular format, packages installed in an environment
   */
  suspend fun list(): PyResult<String> = TODO()

  /**
   * Show information about one or more installed packages
   */
  suspend fun show(): PyResult<String> = TODO()

  /**
   * Display the dependency tree for an environment
   */
  suspend fun tree(): PyResult<String> = TODO()

  /**
   * Verify installed packages have compatible dependencies
   */
  suspend fun check(): PyResult<Unit> = TODO()
}
