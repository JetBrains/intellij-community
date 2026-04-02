// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@ApiStatus.Internal
@Serializable
data class TerminalStartupOptionsDto(
  val shellCommand: List<String>,
  val workingDirectory: String,
  val envVariables: Map<String, String>,
  val processType: TerminalProcessType,
  val pid: Long?,
)

@ApiStatus.Internal
fun TerminalStartupOptions.toDto(): TerminalStartupOptionsDto {
  return TerminalStartupOptionsDto(
    shellCommand = shellCommand,
    workingDirectory = workingDirectory,
    envVariables = envVariables,
    processType = processType,
    pid = pid,
  )
}

@ApiStatus.Internal
fun TerminalStartupOptionsDto.toOptions(): TerminalStartupOptions {
  return TerminalStartupOptionsImpl(
    shellCommand = shellCommand,
    workingDirectory = workingDirectory,
    envVariables = envVariables,
    processType = processType,
    pid = pid,
  )
}