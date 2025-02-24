// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellRuntimeContext

internal class ShellRuntimeDataGeneratorImpl<T : Any>(
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
    return "ShellRuntimeDataGeneratorImpl(debugName=$debugName)"
  }
}
