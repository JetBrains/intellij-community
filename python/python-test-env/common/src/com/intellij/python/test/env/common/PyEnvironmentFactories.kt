// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.python.test.env.core.CachingPyEnvironmentFactory
import com.intellij.python.test.env.core.DefaultPyEnvironmentFactory
import java.nio.file.Files

fun createPyEnvironmentFactory(): CachingPyEnvironmentFactory =
  createPyEnvironmentFactory(Thread.currentThread().contextClassLoader)

fun createPyEnvironmentFactory(providerClassLoader: ClassLoader): CachingPyEnvironmentFactory {
  val workingDir = Files.createTempDirectory("pytest_java_" + System.nanoTime() + "_")
  val cachingPyEnvironmentFactory = CachingPyEnvironmentFactory(
    DefaultPyEnvironmentFactory(workingDir, providerClassLoader)
  )
  return cachingPyEnvironmentFactory
}
