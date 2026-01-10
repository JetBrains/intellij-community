// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.util.text.SemVer
import org.jetbrains.annotations.ApiStatus

/**
 * Base specification for Python environment requirements for tests.
 * 
 * Use type-specific builder functions instead of this class directly:
 */
@ApiStatus.Internal
abstract class PyEnvironmentSpec<SELF: PyEnvironmentSpec<SELF>> {
  var pythonVersion: SemVer = LATEST_PYTHON_VERSION

  private val _libraries = mutableListOf<String>()
  val libraries: List<String> get() = _libraries

  /**
   * DSL builder for specifying libraries
   */
  fun libraries(block: LibrariesBuilder.() -> Unit) {
    val builder = LibrariesBuilder()
    builder.block()
    _libraries.addAll(builder.libraries)
  }

  /**
   * Generate a cache key for this environment specification.
   * The key uniquely identifies the environment configuration.
   *
   * @return Cache key string
   */
  abstract fun toCacheKey(): CacheKey

  /**
   * Helper to create a cache key from common components.
   */
  protected fun buildCacheKey(providerType: String, vararg additionalComponents: String): CacheKey {
    val components = mutableListOf(providerType, pythonVersion.toString())
    components.addAll(additionalComponents)
    if (libraries.isNotEmpty()) {
      components.add("libs:" + libraries.sorted().joinToString(","))
    }
    return CacheKey(components.joinToString("|"))
  }

  fun pythonVersion(version: String): SemVer {
    return parsePythonVersion(version)
  }
}

/**
 * Builder for library specifications
 */
@ApiStatus.Internal
class LibrariesBuilder {
  internal val libraries = mutableListOf<String>()

  /**
   * Add a library with version specification
   * Example: +"numpy==2.0.2" or +"pandas>=1.5.0"
   */
  operator fun String.unaryPlus() {
    libraries.add(this)
  }
}

@JvmInline
value class CacheKey(val value: String)
