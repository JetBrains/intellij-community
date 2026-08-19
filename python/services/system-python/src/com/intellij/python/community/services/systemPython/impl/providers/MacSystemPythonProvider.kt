// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isMac
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path


internal class MacSystemPythonProvider : SystemPythonProvider {

  private val directories = listOf(
    Path.of("/usr/local/bin"),
    Path.of("/usr/local/Cellar/python/bin"),
    Path.of("/Library/Frameworks/Python.framework/Versions/bin"),
    Path.of("/System/Library/Frameworks/Python.framework/Versions/bin"),
  )

  private val xcodePythonDirectories = listOf(
    Path.of("/usr/bin"),
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

    val directoriesToSearch = buildList {
      addAll(directories)
      if (MacPythonSdkFlavor.areCommandLineDeveloperToolsAvailable()) {
        addAll(xcodePythonDirectories)
      }
    }

    val pythons = withContext(Dispatchers.IO) {
      return@withContext collectPythonsInPaths(directoriesToSearch, names)
    }

    return PyResult.success(pythons)
  }

  override val uiCustomization: PyToolUIInfo?
    get() {
      // TODO:
      return null
    }
}