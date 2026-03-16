// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CachingPyEnvironmentFactory(
  private val wrapper: PyEnvironmentFactory,
) : PyEnvironmentFactory {

  private val cache = PyEnvironmentCache()

  override suspend fun createEnvironment(factory: PyEnvironmentFactory, spec: PyEnvironmentSpec<*>): PyEnvironment {
    return cache.getOrCreate(spec.toCacheKey()) {
      wrapper.createEnvironment(factory, spec)
    }
  }

  override fun close() {
    cache.close()
  }
}
