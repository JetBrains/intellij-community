// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.util.Key
import com.intellij.remote.ProcessControlWithMappings
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.python.debugger.PositionConverterProvider
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyPositionConverter
import com.jetbrains.python.debugger.createTargetedPositionConverter
import com.jetbrains.python.remote.PyRemotePathMapper
import java.nio.charset.Charset

/**
 * @param isMostlySilentProcess if the parameter is `true` then [BaseOutputReader.Options.BLOCKING] reader policy is used by default, which
 * avoids "busy-wait" loop and keeps CPU less busy; otherwise the default reader policy defined for local processes is used, which is
 * [BaseOutputReader.Options.NON_BLOCKING] at the moment, allowing to detach IDE from the process without closing its standard streams and
 * terminating the process
 *
 * @see com.intellij.util.io.BaseDataReader.SleepingPolicy
 */
internal class ProcessHandlerWithPyPositionConverter(process: Process,
                                                     commandLine: String,
                                                     charset: Charset,
                                                     private val pathMapper: PyRemotePathMapper,
                                                     private val isMostlySilentProcess: Boolean)
  : KillableProcessHandler(process, commandLine, charset), PositionConverterProvider, ProcessControlWithMappings, ColoredTextAcceptor {

  private val myAnsiEscapeDecoder = AnsiEscapeDecoder()
  override fun createPositionConverter(debugProcess: PyDebugProcess): PyPositionConverter =
    createTargetedPositionConverter(debugProcess, pathMapper)

  override fun getMappingSettings(): PathMapper = pathMapper

  override fun getFileMappings(): List<PathMappingSettings.PathMapping> = emptyList()

  override fun readerOptions(): BaseOutputReader.Options {
    return if (isMostlySilentProcess && !hasPty()) {
      BaseOutputReader.Options.forMostlySilentProcess()
    } else {
      super.readerOptions()
    }
  }

  override fun notifyTextAvailable(text: String, outputType: Key<*>) {
    if (hasPty()) {
      super.notifyTextAvailable(text, outputType)
    }
    else {
      myAnsiEscapeDecoder.escapeText(text, outputType, this)
    }
  }

  override fun coloredTextAvailable(text: String, attributes: Key<*>) {
    super.notifyTextAvailable(text, attributes)
  }
}