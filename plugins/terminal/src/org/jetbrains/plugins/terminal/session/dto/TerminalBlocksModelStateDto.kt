// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandBlockImpl
import org.jetbrains.plugins.terminal.session.TerminalBlocksModelState

@Serializable
@ApiStatus.Internal
data class TerminalBlocksModelStateDto(
  val blocks: List<TerminalCommandBlockDto>,
  val blockIdCounter: Int,
)

@ApiStatus.Internal
fun TerminalBlocksModelState.toDto(): TerminalBlocksModelStateDto {
  val blocksDto = blocks.map {
    // Every block implementation should also provide DTO.
    // Use 'when' here to break compilation there if a new block implementation is added.
    when (it) {
      is TerminalCommandBlockImpl -> it.toDto()
    }
  }
  return TerminalBlocksModelStateDto(blocksDto, blockIdCounter)
}

@ApiStatus.Internal
fun TerminalBlocksModelStateDto.toState(): TerminalBlocksModelState {
  return TerminalBlocksModelState(blocks.map { it.toCommandBlock() }, blockIdCounter)
}