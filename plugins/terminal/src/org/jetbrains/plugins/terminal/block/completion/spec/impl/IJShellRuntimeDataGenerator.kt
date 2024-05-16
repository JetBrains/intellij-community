// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator

internal interface ShellCacheableDataGenerator<T> : ShellRuntimeDataGenerator<T> {
  fun getCacheKey(context: ShellRuntimeContext): String?
}

internal class IJShellRuntimeDataGenerator<T>(
  private val debugName: String?,
  private val cacheKeyFunc: (ShellRuntimeContext) -> String?,
  private val generatorFunc: suspend (ShellRuntimeContext) -> T
) : ShellCacheableDataGenerator<T> {
  override suspend fun generate(context: ShellRuntimeContext): T {
    return generatorFunc(context)
  }

  override fun getCacheKey(context: ShellRuntimeContext): String? {
    return cacheKeyFunc(context)
  }

  override fun toString(): String {
    return "IJShellRuntimeDataGenerator${debugName?.let { ": $debugName" } ?: ""}"
  }
}