// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.jetbrains.python.PythonBinary
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet

/**
 * Register tests pythons as system pythons
 */
internal class EnvTestPythonProvider : SystemPythonProvider {
  override suspend fun findSystemPythons(eelApi: EelApi): Result<Set<PythonBinary>> {
    var pythons = emptySet<PythonBinary>()
    if (eelApi == localEel) {
      pythons = TypeVanillaPython3
        .getTestEnvironments()
        .map { (python, closeable) ->
          Disposer.register(ApplicationManager.getApplication()) {
            closeable.close()
          }

          python
        }.toSet()
    }

    return Result.success(pythons)
  }
}