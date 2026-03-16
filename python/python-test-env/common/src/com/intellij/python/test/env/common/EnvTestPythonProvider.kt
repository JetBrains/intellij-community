// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Register tests pythons as system pythons
 */
class EnvTestPythonProvider(private val factory: PyEnvironmentFactory) : SystemPythonProvider {

  private val localPythons by lazy {
    runBlocking(Dispatchers.IO) {
      listOf(
        PredefinedPyEnvironments.VANILLA_3_12,
      // Add Py27 temporary to test Py27
        PredefinedPyEnvironments.VANILLA_2_7,
      )
        .map { factory.createEnvironment(it) }
        .map { it.pythonPath }
        .toSet()
    }
  }

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
      // It is perfectly valid not to find any python because some tests might run without a python and still have this module on a class-path
    val pythons = if (eelApi == localEel) {
      localPythons
    } else {
      emptySet()
    }

    return Result.Companion.success(pythons)
  }
}