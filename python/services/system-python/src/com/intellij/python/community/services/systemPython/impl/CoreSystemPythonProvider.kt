// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import kotlinx.coroutines.Dispatchers

/**
 * [SystemPythonProvider] based ob [PythonSdkFlavor] (kind of bridge)
 */
internal object CoreSystemPythonProvider : SystemPythonProvider {
  override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
    if (eelApi != localEel) return Result.success(emptySet())
    with(Dispatchers.IO) {
      val paths = PythonSdkFlavor.getApplicableFlavors(false)
        .flatMap {
          it.dropCaches()
          it.suggestLocalHomePaths(null, null)
        }
      return Result.success(paths.toSet())
    }
  }
}