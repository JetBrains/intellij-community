// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isMac
import com.intellij.python.community.services.shared.UICustomization
import com.intellij.python.community.services.systemPython.PythonCommunityServicesSystemPythonIcons
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class BrewSystemPythonProvider : SystemPythonProvider {
  private val LOGGER: Logger = Logger.getInstance(BrewSystemPythonProvider::class.java)
  private val binDirectory = Path.of("/opt/homebrew/bin")

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    if (!eelApi.platform.isMac) {
      return PyResult.success(emptySet())
    }

    val pythons = withContext(Dispatchers.IO) {
      try {
        return@withContext collectPythonsInPaths(eelApi, listOf(binDirectory), listOf(python3XNamePattern))
      }
      catch (e: RuntimeException) {
        LOGGER.error("failed to discover brew pythons", e)
      }

      return@withContext emptySet()
    }

    return PyResult.success(pythons)
  }

  override val uiCustomization: UICustomization?
    get() {
      // TODO: proper icon
      return UICustomization(title = "homebrew", icon = PythonCommunityServicesSystemPythonIcons.Homebrew)
    }
}
