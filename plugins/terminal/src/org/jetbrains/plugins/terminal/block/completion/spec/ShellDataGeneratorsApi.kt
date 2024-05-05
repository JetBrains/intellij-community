// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.impl.IJShellRuntimeDataGenerator

@ApiStatus.Experimental
fun <T> ShellRuntimeDataGenerator(
  cacheKeyAndDebugName: String? = null,
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return IJShellRuntimeDataGenerator(cacheKeyAndDebugName, { cacheKeyAndDebugName }, generate)
}

@ApiStatus.Experimental
fun <T> ShellRuntimeDataGenerator(
  debugName: String? = null,
  getCacheKey: (ShellRuntimeContext) -> String? = { null },
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return IJShellRuntimeDataGenerator(debugName, getCacheKey, generate)
}