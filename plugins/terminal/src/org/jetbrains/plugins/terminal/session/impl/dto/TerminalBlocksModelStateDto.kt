// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.impl.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock

@Serializable
@ApiStatus.Internal
data class TerminalBlocksModelStateDto(
  val blocks: List<TerminalCommandBlockDto>,
  val blockIdCounter: Int,
)

@ApiStatus.Internal
fun TerminalBlocksModelState.toDto(): TerminalBlocksModelStateDto {
  val blocksDto = blocks.map {
    when (it) {
      is TerminalCommandBlock -> it.toDto()
      else -> error("Unexpected block type: ${it::class.java}, provide serialization for it.")
    }
  }
  return TerminalBlocksModelStateDto(blocksDto, blockIdCounter)
}

@ApiStatus.Internal
fun TerminalBlocksModelStateDto.toState(): TerminalBlocksModelState {
  return TerminalBlocksModelState(blocks.map { it.toCommandBlock() }, blockIdCounter)
}