// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.UICustomization
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.asKotlinResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.hasUvExecutable
import java.nio.file.Path

internal class UvSystemPythonProvider : SystemPythonProvider {
  override suspend fun findSystemPythons(eelApi: EelApi): Result<Set<PythonBinary>> {
    if (eelApi != localEel || !hasUvExecutable()) {
      // TODO: support for remote execution
      return Result.success(emptySet())
    }

    val uv = createUvLowLevel(Path.of("."))
    return uv.listUvPythons().asKotlinResult()
  }

  @Suppress("HardCodedStringLiteral") // tool name is untranslatable
  override val uiCustomization: UICustomization = UICustomization("uv", PythonIcons.UV)
}