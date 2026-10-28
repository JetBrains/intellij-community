// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.readOnly

import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.osFamily
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.jetbrains.python.PyBundle

/**
 * A system-wide Python on Unix or macOS is read-only. A virtual environment of it is not. A target interpreter has no
 * detected environment, so this provider never makes it read-only.
 */
internal class PythonSystemSdkReadOnlyProvider : PythonSdkReadOnlyProvider {
  override fun isReadOnly(interpreter: PythonInterpreter): Boolean = when (interpreter) {
    is NativePythonInterpreter -> interpreter.pythonEnvironment is SystemPythonEnvironment && !interpreter.pythonBinary.osFamily.isWindows
    is TargetPythonInterpreter -> false
  }

  override fun getReadOnlyMessage(interpreter: PythonInterpreter): String =
    PyBundle.message("read.only.python.sdk.system.wide.read.only.message")
}
