// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.systemPythonSpi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.systemPythonSpi.SystemPythonProvider.Companion.EP

/**
 * Register [EP] to [findSystemPythons]
 */
interface SystemPythonProvider {
  companion object {
    val EP: ExtensionPointName<SystemPythonProvider> = ExtensionPointName<SystemPythonProvider>("Pythonid.systemPythonProvider")
  }

  suspend fun findSystemPythons(eelApi: EelApi): Result<Set<PythonBinary>>
}