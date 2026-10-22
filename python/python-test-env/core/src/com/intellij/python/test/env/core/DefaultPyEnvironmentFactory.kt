// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Default implementation of [PyEnvironmentFactory] that delegates to registered providers.
 * 
 * Java SPI supplies the providers. The factory uses the first provider that supports the specification.
 */
class DefaultPyEnvironmentFactory(
  override val workingDir: Path,
  private val providerClassLoader: ClassLoader,
): PyEnvironmentFactory {

  override suspend fun createEnvironment(factory: PyEnvironmentFactory, spec: PyEnvironmentSpec<*>): PyEnvironment {
    for (provider in ServiceLoader.load(PyEnvironmentProvider::class.java, providerClassLoader)) {
      if (provider.supportedSpecType.isAssignableFrom(spec::class.java)) {
        val context = object : PyEnvironmentProvider.Context {
          override val factory: PyEnvironmentFactory = factory
          override val workingDir: Path = this@DefaultPyEnvironmentFactory.workingDir
          override val cacheDir: Path = defaultPyEnvDownloadCache.cacheDirectory()
        }
        return setupEnvironment(provider, context, spec)
      }
    }
    error("No provider found for environment specification: $spec")
  }

  override fun close() {}
}

private suspend fun <S : PyEnvironmentSpec<S>> setupEnvironment(
  provider: PyEnvironmentProvider<S>,
  context: PyEnvironmentProvider.Context,
  spec: PyEnvironmentSpec<*>,
): PyEnvironment = provider.setupEnvironment(context, provider.supportedSpecType.cast(spec))
