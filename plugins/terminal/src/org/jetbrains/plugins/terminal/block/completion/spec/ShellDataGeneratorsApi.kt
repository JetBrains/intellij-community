// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeDataGeneratorImpl

/**
 * Creates a [ShellRuntimeDataGenerator] that provides the result of type [T] with the help of [ShellRuntimeContext].
 *
 * @param cacheKeyAndDebugName the unique key used for caching the result of [generate] function and also as a debug name of the generator object.
 * Use [createCacheKey][org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey] utility
 * to create a key in a suitable format.
 * **The generator result is cached until the user executes the next command in the Terminal**.
 * If the cache key is null, the generator won't be cached.
 */
@ApiStatus.Experimental
fun <T : Any> ShellRuntimeDataGenerator(
  cacheKeyAndDebugName: String? = null,
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return ShellRuntimeDataGeneratorImpl(cacheKeyAndDebugName, { cacheKeyAndDebugName }, generate)
}

/**
 * Creates a [ShellRuntimeDataGenerator] that provides the result of type [T] with the help of [ShellRuntimeContext].
 *
 * @param debugName the value for [toString] method of the generator object.
 * @param getCacheKey the way to specify [ShellRuntimeContext] dependant cache key.
 * Can be useful in case when we should provide different values depending on typed prefix.
 * For example, see [fileSuggestionsGenerator][org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator].
 * **The generator result is cached until the user executes the next command in the Terminal**.
 * If the cache key is null, the generator won't be cached.
 */
@ApiStatus.Experimental
fun <T : Any> ShellRuntimeDataGenerator(
  debugName: String? = null,
  getCacheKey: (ShellRuntimeContext) -> String? = { null },
  generate: suspend (ShellRuntimeContext) -> T
): ShellRuntimeDataGenerator<T> {
  return ShellRuntimeDataGeneratorImpl(debugName, getCacheKey, generate)
}
