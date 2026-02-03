// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.python.community.execService.python.execGetBoolFromStdout
import com.intellij.python.community.services.shared.VanillaPythonWithPythonInfo
import com.intellij.python.community.services.systemPython.SysPythonRegisterError
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import org.intellij.lang.annotations.Language

internal fun PyError.asSysPythonRegisterError(): SysPythonRegisterError.PythonIsBroken = SysPythonRegisterError.PythonIsBroken(this)

@Language("Python")
internal const val ENSURE_SYSTEM_PYTHON_CMD = "import sys; print(sys.prefix == sys.base_prefix)"

internal suspend fun ensureSystemPython(python: VanillaPythonWithPythonInfo): PyResult<Boolean> {
  if (python.pythonInfo.languageLevel.isPython2) {
    // there is no obvious check for venv in py2.7. Nobody uses it, anyway. Let it be.
    return Result.success(true)
  }
  val systemPython = python.asExecutablePython.execGetBoolFromStdout(ENSURE_SYSTEM_PYTHON_CMD).getOr { return it }
  return Result.success(systemPython)
}