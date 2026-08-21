// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.PyTool
import com.jetbrains.python.sdk.PythonInterpreter
import java.nio.file.Path
import kotlin.io.path.isExecutable

/** The tool's own executable name on [this] OS — Windows wants the `.exe`. */
private fun EelOsFamily.executableName(binaryName: String): String = when (this) {
  EelOsFamily.Posix -> binaryName
  EelOsFamily.Windows -> "$binaryName.exe"
}

/**
 * [tool]'s executable installed *into* this interpreter's environment — its scripts directory, beside the interpreter
 * binary — or `null` when the tool is not installed there.
 *
 * The receiver is the interpreter because that is what determines where to look: the answer is a property of this
 * environment, not of the tool. Only local interpreters are supported for now.
 */
fun PythonInterpreter.findToolExecutable(tool: PyTool, executableName: String = tool.packageName.name): Path? =
  pythonBinaryPath?.let { binary ->
    val osFamily = binary.getEelDescriptor().osFamily
    binary.resolveSibling(osFamily.executableName(executableName)).takeIf { it.isExecutable() }
  }
