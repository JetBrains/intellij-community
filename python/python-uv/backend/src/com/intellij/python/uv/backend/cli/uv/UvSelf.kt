// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage the uv executable
 */
@Suppress("unused")
class UvSelf(runtime: PyToolRuntime) : UvCommand("self", runtime) {
  /**
   * Update uv to the latest released version.
   */
  suspend fun update(targetVersion: String? = null): PyResult<String> {
    val arguments = if (targetVersion == null) emptyArray() else arrayOf(targetVersion)
    return executeAndHandleErrors("update", *arguments, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Display uv's version
   */
  suspend fun version(short: Boolean? = null): PyResult<String> {
    val options = listOf(short to "--short").makeOptions()
    return executeAndHandleErrors("version", *options, transformer = ZeroCodeStdoutTransformer)
  }
}
