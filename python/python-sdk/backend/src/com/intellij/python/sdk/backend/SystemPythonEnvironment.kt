// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import org.jetbrains.annotations.ApiStatus

/**
 * A system-wide Python installation: no root of its own, no library of its own, nothing to activate.
 */
@ApiStatus.Internal
data class SystemPythonEnvironment(
  /** Always null: a system interpreter records nothing about itself, so its version is only known by running it. */
  override val version: @NlsSafe String? = null,
  override val pythonBinaryPath: PythonBinary,
) : PythonEnvironment

/**
 * Whatever no other provider claims.
 *
 * Register it last, because it answers for any layout.
 */
internal class SystemPythonEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = SystemPythonEnvironment::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment> =
    SystemPythonEnvironment(pythonBinaryPath = pythonBinary).let { PyResult.success(it) }
}
