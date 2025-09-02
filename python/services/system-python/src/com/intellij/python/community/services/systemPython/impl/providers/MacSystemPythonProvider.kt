// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isMac
import com.intellij.python.community.services.shared.UICustomization
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class MacSystemPythonProvider : SystemPythonProvider {
  private val LOGGER: Logger = Logger.getInstance(MacSystemPythonProvider::class.java)

  private val directories = listOf(
    Path.of("/usr/bin"),
    Path.of("/usr/local/bin"),
    Path.of("/usr/local/Cellar/python"),
    Path.of("/Library/Frameworks/Python.framework/Versions"),
    Path.of("/System/Library/Frameworks/Python.framework/Versions"),
  )

  // Patterns to match Python executable filenames
  private val names = listOf(
    python3NamePattern,
    python3XNamePattern,
    pypyNamePattern,
  )

  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    // Check if we're on a Unix system that's not Mac
    if (!eelApi.platform.isMac || useLegacyPythonProvider()) {
      return PyResult.success(emptySet())
    }

    val pythons = withContext(Dispatchers.IO) {
      try {
        return@withContext collectPythonsInPaths(eelApi, directories, names)
      }
      catch (e: RuntimeException) {
        LOGGER.error("Failed to discover mac system pythons", e)
      }

      return@withContext emptySet()
    }

    return PyResult.success(pythons)
  }

  override val uiCustomization: UICustomization?
    get() {
      // TODO:
      return null
    }
}