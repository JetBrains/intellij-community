// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyConsoleProcessHandlers")

package com.jetbrains.python.console

import com.google.common.net.HostAndPort
import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Pair
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyPositionConverter
import com.jetbrains.python.remote.PyRemoteSocketToLocalHostProvider
import com.jetbrains.python.remote.RemoteDebuggableProcessHandler
import java.nio.charset.Charset

/**
 * Returns an instance of [PyConsoleProcessHandler] that either:
 * - delegates base methods to provided [processHandler] *if it implements [RemoteDebuggableProcessHandler]*,
 * - or uses [process] to create [PyConsoleProcessHandler] *in other case*.
 */
internal fun createPythonConsoleProcessHandler(processHandler: ProcessHandler,
                                               process: Process,
                                               consoleView: PythonConsoleView,
                                               pydevConsoleCommunication: PydevConsoleCommunication,
                                               commandLine: String,
                                               charset: Charset): PyConsoleProcessHandler =
  if (processHandler is RemoteDebuggableProcessHandler) {
    PyConsoleProcessHandlerWrapper(processHandler, process, consoleView, pydevConsoleCommunication, commandLine, charset)
  }
  else {
    PyConsoleProcessHandler(process, consoleView, pydevConsoleCommunication, commandLine, charset)
  }

private class PyConsoleProcessHandlerWrapper(private val processHandler: RemoteDebuggableProcessHandler,
                                             process: Process,
                                             consoleView: PythonConsoleView,
                                             pydevConsoleCommunication: PydevConsoleCommunication,
                                             commandLine: String,
                                             charset: Charset)
  : PyConsoleProcessHandler(process, consoleView, pydevConsoleCommunication, commandLine, charset),
    RemoteDebuggableProcessHandler,
    KillableProcess {
  override fun getFileMappings(): List<PathMappingSettings.PathMapping> = processHandler.fileMappings

  override fun getRemoteSocket(localPort: Int): Pair<String, Int> = processHandler.getRemoteSocket(localPort)

  override fun getLocalTunnel(remotePort: Int): HostAndPort? = processHandler.getLocalTunnel(remotePort)

  override fun getRemoteSocketToLocalHostProvider(): PyRemoteSocketToLocalHostProvider = processHandler.remoteSocketToLocalHostProvider

  override fun createPositionConverter(debugProcess: PyDebugProcess): PyPositionConverter =
    processHandler.createPositionConverter(debugProcess)
}