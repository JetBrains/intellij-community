// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi
import com.jetbrains.python.PythonBinary

/**
 * Register [EP] to [findSystemPythons]
 */
interface SystemPythonProvider {
  companion object {
    internal val EP: ExtensionPointName<SystemPythonProvider> = ExtensionPointName<SystemPythonProvider>("Pythonid.systemPythonProvider")
  }

  suspend fun findSystemPythons(eelApi: EelApi): Set<PythonBinary>
}