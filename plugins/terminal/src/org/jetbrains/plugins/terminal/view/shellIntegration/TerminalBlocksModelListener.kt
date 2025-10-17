// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
interface TerminalBlocksModelListener : EventListener {
  fun blockAdded(event: TerminalBlockAddedEvent) {}

  fun blockRemoved(event: TerminalBlockRemovedEvent) {}

  fun blocksReplaced(event: TerminalBlocksReplacedEvent) {}
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlocksModelEvent {
  val model: TerminalBlocksModel
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockAddedEvent : TerminalBlocksModelEvent {
  val block: TerminalBlockBase
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockRemovedEvent : TerminalBlocksModelEvent {
  val block: TerminalBlockBase
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlocksReplacedEvent : TerminalBlocksModelEvent {
  val oldBlocks: List<TerminalBlockBase>
  val newBlocks: List<TerminalBlockBase>
}