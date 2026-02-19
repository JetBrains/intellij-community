// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockId
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalCommandBlockImpl

@ApiStatus.Internal
@Serializable
data class TerminalCommandBlockDto(
  val id: TerminalBlockId,
  val startOffset: Long,
  val endOffset: Long,
  val commandStartOffset: Long?,
  val outputStartOffset: Long?,
  val workingDirectory: String?,
  val executedCommand: String?,
  val exitCode: Int?,
)

@ApiStatus.Internal
fun TerminalCommandBlock.toDto(): TerminalCommandBlockDto {
  return TerminalCommandBlockDto(
    id = id,
    startOffset = startOffset.toAbsolute(),
    endOffset = endOffset.toAbsolute(),
    commandStartOffset = commandStartOffset?.toAbsolute(),
    outputStartOffset = outputStartOffset?.toAbsolute(),
    workingDirectory = workingDirectory,
    executedCommand = executedCommand,
    exitCode = exitCode,
  )
}

@ApiStatus.Internal
fun TerminalCommandBlockDto.toCommandBlock(): TerminalCommandBlock {
  return TerminalCommandBlockImpl(
    id = id,
    startOffset = TerminalOffset.of(startOffset),
    endOffset = TerminalOffset.of(endOffset),
    commandStartOffset = commandStartOffset?.let { TerminalOffset.of(it) },
    outputStartOffset = outputStartOffset?.let { TerminalOffset.of(it) },
    workingDirectory = workingDirectory,
    executedCommand = executedCommand,
    exitCode = exitCode,
  )
}