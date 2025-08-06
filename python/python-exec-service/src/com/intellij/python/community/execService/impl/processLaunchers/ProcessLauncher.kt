// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl.processLaunchers

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.provider.utils.ProcessFunctions
import com.intellij.python.community.execService.Args
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.ExecErrorReason
import kotlinx.coroutines.CoroutineScope

private val logger = fileLogger()

internal class ProcessLauncher(
  val exeForError: Exe,
  val args: List<String>,
  private val processCommands: ProcessCommands,
) {
  suspend fun start(): Result<Process, ExecErrorReason.CantStart> = processCommands.start()
  suspend fun killAndJoin() {
    processCommands.processFunctions.killAndJoin(logger, exeForError.toString())
  }
}

internal interface ProcessCommands {
  suspend fun start(): Result<Process, ExecErrorReason.CantStart>
  val processFunctions: ProcessFunctions
}

internal data class LaunchRequest(
  val scopeToBind: CoroutineScope,
  val args: Args,
  val env: Map<String, String>,
)