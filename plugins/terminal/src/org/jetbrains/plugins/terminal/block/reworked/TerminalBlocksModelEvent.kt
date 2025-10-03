// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalOutputBlock

@ApiStatus.Internal
sealed interface TerminalBlocksModelEvent {
  val block: TerminalOutputBlock
}

@ApiStatus.Internal
data class TerminalBlockStartedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent

@ApiStatus.Internal
data class TerminalBlockFinishedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent

@ApiStatus.Internal
data class TerminalBlockRemovedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent