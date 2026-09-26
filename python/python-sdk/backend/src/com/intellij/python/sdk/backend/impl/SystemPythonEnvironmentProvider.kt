// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.impl

import com.intellij.python.sdk.backend.PythonEnvironment
import com.intellij.python.sdk.backend.PythonEnvironmentProvider
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult

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
