// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.session.TerminalOutputBlock

@Serializable
@ApiStatus.Internal
data class TerminalOutputBlockDto(
  val id: Int,
  val startOffset: Long,
  val commandStartOffset: Long?,
  val outputStartOffset: Long?,
  val endOffset: Long,
  val exitCode: Int?,
)

@ApiStatus.Internal
fun TerminalOutputBlock.toDto(): TerminalOutputBlockDto {
  return TerminalOutputBlockDto(
    id = id,
    startOffset = startOffset.toAbsolute(),
    commandStartOffset = commandStartOffset?.toAbsolute(),
    outputStartOffset = outputStartOffset?.toAbsolute(),
    endOffset = endOffset.toAbsolute(),
    exitCode = exitCode
  )
}

@ApiStatus.Internal
fun TerminalOutputBlockDto.toBlock(outputModel: TerminalOutputModel): TerminalOutputBlock {
  return TerminalOutputBlock(
    id = id,
    startOffset = outputModel.absoluteOffset(startOffset),
    commandStartOffset = commandStartOffset?.let { outputModel.absoluteOffset(it) },
    outputStartOffset = outputStartOffset?.let { outputModel.absoluteOffset(it) },
    endOffset = outputModel.absoluteOffset(endOffset),
    exitCode = exitCode
  )
}