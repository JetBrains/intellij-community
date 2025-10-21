// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.provider.utils.ProcessFunctions
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.TtySize
import com.intellij.python.community.execService.impl.LoggingProcess
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

private val logger = fileLogger()

internal class ProcessLauncher(
  val exeForError: Exe,
  val args: List<String>,
  private val processCommands: ProcessCommands,
) {
  suspend fun start(): Result<LoggingProcess, ExecErrorReason.CantStart> =
    processCommands.start()
      .mapSuccess {
        LoggingProcess(
          it,
          processCommands.scopeToBind.coroutineContext[TraceContext.Key],
          Clock.System.now(),
          processCommands.info.cwd,
          exeForError,
          args,
          processCommands.info.env,
          processCommands.info.target,
        )
      }

  suspend fun killAndJoin() {
    processCommands.processFunctions.killAndJoin(logger, exeForError.toString())
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
  val env: Map<String, String>,
  val usePty: TtySize?,
)