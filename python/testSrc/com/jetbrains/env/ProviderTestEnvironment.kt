// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private class ProviderTestEnvironment(private val factory: PyEnvironmentFactory, private val spec: PyEnvironmentSpec<*>, private val tags: Set<String>) :
  PyTestEnvironment {
  private var environment: PyEnvironment? = null

  override fun getPythonVersion(): SemVer {
    return spec.pythonVersion
  }

  override fun getDescription(): String {
    return "provider: " + spec.javaClass.getSimpleName() + " (Python " + spec.pythonVersion + ")"
  }

  override fun getTags(): Set<String> {
    return tags
  }

  override fun prepareSdk(): Sdk = runBlocking(Dispatchers.IO) {
    factory.createEnvironment(spec).prepareSdk()
  }

  override fun close() {
    environment?.close()
  }
}
