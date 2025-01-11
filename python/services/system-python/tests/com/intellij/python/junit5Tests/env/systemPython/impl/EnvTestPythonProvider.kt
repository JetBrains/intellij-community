// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.spi.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.tools.PythonType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet

/**
 * Register tests pythons as system pythons
 */
internal class EnvTestPythonProvider : SystemPythonProvider {
  override suspend fun findSystemPythons(eelApi: EelApi): Set<PythonBinary> {
    if (eelApi != localEel) return emptySet()
    return PythonType.VanillaPython3
      .getTestEnvironments()
      .map { (python, closeable) ->
        Disposer.register(ApplicationManager.getApplication()) {
          closeable.close()
        }
        python
      }
      .toSet()
  }
}