// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.hasUvExecutable
import com.jetbrains.python.systemPythonSpi.SystemPythonProvider
import java.nio.file.Path

class UvSystemPythonProvider : SystemPythonProvider {
  override suspend fun findSystemPythons(eelApi: EelApi): Result<Set<PythonBinary>> {
    if (eelApi != localEel || !hasUvExecutable()) {
      // TODO: support for remote execution
      return Result.success(emptySet())
    }

    val uv = createUvLowLevel(Path.of("."))
    return uv.discoverUvInstalledPythons()
  }
}