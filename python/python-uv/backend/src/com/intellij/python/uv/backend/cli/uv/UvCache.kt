// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.cli.uv

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Manage uv's cache
 */
@Suppress("unused")
class UvCache(runtime: PyToolRuntime) : UvCommand("cache", runtime) {
  /**
   * Clear the cache, removing all entries or those linked to specific packages
   */
  suspend fun clean(vararg packages: String): PyResult<String> {
    return executeAndHandleErrors("clean", *packages, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Prune all unreachable objects from the cache
   */
  suspend fun prune(ciFriendly: Boolean? = null): PyResult<String> {
    val options = listOf(ciFriendly to "--ci").makeOptions()
    return executeAndHandleErrors("prune", *options, transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Show the cache directory
   */
  suspend fun dir(): PyResult<String> {
    return executeAndHandleErrors("dir", transformer = ZeroCodeStdoutTransformer)
  }

  /**
   * Show the cache size
   */
  suspend fun size(): PyResult<String> {
    return executeAndHandleErrors("size", transformer = ZeroCodeStdoutTransformer)
  }
}
