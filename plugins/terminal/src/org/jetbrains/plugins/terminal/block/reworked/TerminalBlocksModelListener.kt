// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.TerminalOutputBlock
import java.util.*

@ApiStatus.Internal
interface TerminalBlocksModelListener : EventListener {
  fun blockAdded(event: TerminalBlockAddedEvent) {}

  fun blockRemoved(event: TerminalBlockRemovedEvent) {}

  fun blocksReplaced(event: TerminalBlocksReplacedEvent) {}
}

@ApiStatus.Internal
sealed interface TerminalBlocksModelEvent {
  val model: TerminalBlocksModel
}

@ApiStatus.Internal
sealed interface TerminalBlockAddedEvent : TerminalBlocksModelEvent {
  val block: TerminalOutputBlock
}

@ApiStatus.Internal
sealed interface TerminalBlockRemovedEvent : TerminalBlocksModelEvent {
  val block: TerminalOutputBlock
}

@ApiStatus.Internal
sealed interface TerminalBlocksReplacedEvent : TerminalBlocksModelEvent {
  val oldBlocks: List<TerminalOutputBlock>
  val newBlocks: List<TerminalOutputBlock>
}