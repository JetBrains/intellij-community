// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.target.TargetEnvironment
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.debugger.PositionConverterProvider
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyPositionConverter
import com.jetbrains.python.debugger.createTargetedPositionConverter
import java.nio.charset.Charset

internal class ProcessHandlerWithPyPositionConverter(process: Process,
                                                     commandLine: String,
                                                     charset: Charset,
                                                     private val targetEnvironment: TargetEnvironment,
                                                     private val pathMappingSettings: PathMappingSettings)
  : KillableColoredProcessHandler(process, commandLine, charset), PositionConverterProvider {
  override fun createPositionConverter(debugProcess: PyDebugProcess): PyPositionConverter =
    createTargetedPositionConverter(debugProcess, targetEnvironment, pathMappingSettings)
}