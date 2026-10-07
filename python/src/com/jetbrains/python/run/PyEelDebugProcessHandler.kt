// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.platform.eel.EelDescriptor
import com.jetbrains.python.debugger.PositionConverterProvider
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyEelPathMapper
import com.jetbrains.python.debugger.PyPositionConverter
import com.jetbrains.python.debugger.createTargetedPositionConverter
import java.nio.charset.Charset

/**
 * The debug process handler for an SDK without a target on the remote [eel], for example WSL in the eel native mode.
 * pydevd reports the paths on [eel], so the breakpoint and the frame positions go through [PyEelPathMapper].
 */
internal class PyEelDebugProcessHandler(
  process: Process,
  commandLine: String,
  charset: Charset,
  private val eel: EelDescriptor,
) : PyDebugProcessHandler(process, commandLine, charset), PositionConverterProvider {
  override fun createPositionConverter(debugProcess: PyDebugProcess): PyPositionConverter =
    createTargetedPositionConverter(debugProcess, PyEelPathMapper(eel))
}
