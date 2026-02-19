// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.plain

import com.intellij.python.test.env.core.CacheKey
import com.intellij.python.test.env.core.PyEnvironmentSpec
import org.jetbrains.annotations.ApiStatus

/**
 * Specification for plain Python venv environment.
 * 
 * Example usage:
 * ```kotlin
 * val env = pythonEnvironment {
 *   pythonVersion = "3.11"
 *   libraries {
 *     +"numpy==2.0.2"
 *     +"pandas>=1.5.0"
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
class PlainPyEnvironmentSpec : PyEnvironmentSpec<PlainPyEnvironmentSpec>() {

  override fun toCacheKey(): CacheKey {
    return buildCacheKey("plain")
  }
}

/**
 * DSL entry point for creating plain Python venv environment specifications.
 *
 * Example:
 * ```kotlin
 * val env = pythonEnvironment {
 *   pythonVersion = "3.11"
 *   libraries {
 *     +"numpy==2.0.2"
 *     +"pandas>=1.5.0"
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
fun pythonEnvironment(block: PlainPyEnvironmentSpec.() -> Unit): PlainPyEnvironmentSpec {
  val spec = PlainPyEnvironmentSpec()
  spec.block()
  return spec
}