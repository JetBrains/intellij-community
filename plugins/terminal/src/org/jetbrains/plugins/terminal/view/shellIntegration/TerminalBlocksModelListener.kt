// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface TerminalBlocksModelListener {
  fun blockAdded(event: TerminalBlockAddedEvent) {}

  /**
   * Can be called when the block range becomes out of the regular [org.jetbrains.plugins.terminal.view.TerminalOutputModel] bounds
   * because of trimming.
   * Or when some text removing operation is performed, for example, executing `clear` command.
   */
  fun blockRemoved(event: TerminalBlockRemovedEvent) {}

  /**
   * Can be called when some mass text replacement operation is performed, for example,
   * when initial state of the [TerminalBlocksModel] is received from the backend.
   */
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
  /** The block that was added to the model */
  val block: TerminalBlockBase
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockRemovedEvent : TerminalBlocksModelEvent {
  /** The block that was removed from the model */
  val block: TerminalBlockBase
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlocksReplacedEvent : TerminalBlocksModelEvent {
  val oldBlocks: List<TerminalBlockBase>
  val newBlocks: List<TerminalBlockBase>
}