// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage authentication
 */
@Suppress("unused")
class UvAuth(runtime: PyToolRuntime) : UvCommand("auth", runtime) {
  /**
   * Login to a service
   */
  suspend fun login(): PyResult<Unit> = TODO()

  /**
   * Logout of a service
   */
  suspend fun logout(): PyResult<Unit> = TODO()

  /**
   * Show the authentication token for a service
   */
  suspend fun token(): PyResult<String> = TODO()

  /**
   * Show the path to the uv credentials directory
   */
  suspend fun dir(): PyResult<String> {
    return executeAndHandleErrors("dir", transformer = ZeroCodeStdoutTransformer)
  }
}
