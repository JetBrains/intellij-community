// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.session.TerminalBlocksModelState

@Serializable
@ApiStatus.Internal
data class TerminalBlocksModelStateDto(
  val blocks: List<TerminalOutputBlockDto>,
  val blockIdCounter: Int,
)

@ApiStatus.Internal
fun TerminalBlocksModelState.toDto(): TerminalBlocksModelStateDto {
  return TerminalBlocksModelStateDto(blocks.map { it.toDto() }, blockIdCounter)
}

@ApiStatus.Internal
fun TerminalBlocksModelStateDto.toState(outputModel: TerminalOutputModel): TerminalBlocksModelState {
  return TerminalBlocksModelState(blocks.map { it.toBlock(outputModel) }, blockIdCounter)
}