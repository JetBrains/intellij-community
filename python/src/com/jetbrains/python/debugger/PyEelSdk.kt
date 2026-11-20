// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter

/**
 * The eel of a native interpreter when it is not the local eel, such as WSL in the eel native mode. The debugger and
 * the console open a tunnel to the IDE for such an interpreter. A target interpreter has none.
 */
internal fun PythonInterpreter.remoteEelOrNull(): EelDescriptor? = when (this) {
  is NativePythonInterpreter -> pythonBinary.getEelDescriptor().takeIf { it != LocalEelDescriptor }
  is TargetPythonInterpreter -> null
}
