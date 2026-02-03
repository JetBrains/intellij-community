// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import java.nio.file.Path

/**
 * Default implementation of [PyEnvironmentFactory] that delegates to registered providers.
 * 
 * Each provider is registered with a predicate that determines if it can handle a given spec.
 * When creating an environment, the factory iterates through providers and uses the first
 * matching one.
 */
class DefaultPyEnvironmentFactory(
  private val workingDir: Path,
  private val providers: List<Pair<(PyEnvironmentSpec<*>) -> Boolean, PyEnvironmentProvider<*>>>
): PyEnvironmentFactory {

  override suspend fun createEnvironment(factory: PyEnvironmentFactory, spec: PyEnvironmentSpec<*>): PyEnvironment {
    for ((predicate, provider) in providers) {
      if (predicate(spec)) {
        val context = object : PyEnvironmentProvider.Context {
          override val factory: PyEnvironmentFactory = factory
          override val workingDir: Path = this@DefaultPyEnvironmentFactory.workingDir
          override val cacheDir: Path = PyEnvDownloadCache.cacheDirectory()
        }
        @Suppress("UNCHECKED_CAST")
        val setupEnv = provider::setupEnvironment as suspend (PyEnvironmentProvider.Context, PyEnvironmentSpec<*>) -> PyEnvironment
        return setupEnv(context, spec)
      }
    }
    error("No provider found for environment specification: $spec")
  }

  override fun close() {}
}
