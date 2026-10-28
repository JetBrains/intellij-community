// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.execution.target.HostPort
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.impl.base.ProcessFunctions
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.community.execService.TtySize
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.impl.LoggingProcessImpl
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

private val logger = fileLogger()

/**
 * The address of the IDE side of a forwarded port, and the address that a process listens on for one.
 */
internal const val LOOPBACK_ADDRESS: String = "127.0.0.1"

internal class ProcessLauncher(
  val exeForError: Exe,
  val args: List<String>,
  val processCommands: ProcessCommands,
  /**
   * Each port of [LaunchRequest.portsToForward] to the address where the IDE connects to it.
   */
  val forwardedPorts: Map<UShort, HostPort> = emptyMap(),
) {
  suspend fun start(weight: ConcurrentProcessWeight? = null): Result<LoggingProcessImpl, ExecErrorReason.CantStart> =
    processCommands.start()
      .mapSuccess {
        LoggingProcessImpl(
          it,
          weight,
          processCommands.scopeToBind.coroutineContext[TraceContext.Key],
          Clock.System.now(),
          processCommands.info.cwd,
          exeForError,
          args,
          processCommands.info.env,
          processCommands.info.target,
          ProcessOutputTopic,
        )
      }

  suspend fun killAndJoin() {
    processCommands.processFunctions.killAndJoin({ logger.warn(it) }, exeForError.toString())
  }
}

internal interface ProcessCommands {
  suspend fun start(): Result<Process, ExecErrorReason.CantStart>
  val processFunctions: ProcessFunctions
  val scopeToBind: CoroutineScope
  val info: ProcessCommandsInfo
}

internal data class ProcessCommandsInfo(
  val env: Map<String, String>,
  val cwd: String?,
  val target: String,
)

internal data class LaunchRequest(
  val scopeToBind: CoroutineScope,
  val args: Args,
  val env: List<EnvVar>,
  val usePty: TtySize?,
  val uploadConfig: UploadConfig? = null,
  val downloadConfig: DownloadConfig? = null,
  /**
   * Ports on the machine of the process that the IDE must reach.
   */
  val portsToForward: Set<UShort> = emptySet(),
)