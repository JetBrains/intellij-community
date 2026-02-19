// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalOutputModelListener {
  fun blockCreated(block: CommandBlock) {}
  fun blockRemoved(block: CommandBlock) {}

  /** Block length is finalized, so block bounds won't expand if the text is added before or after the block. */
  fun blockFinalized(block: CommandBlock) {}
  fun blockInfoUpdated(block: CommandBlock, newInfo: CommandBlockInfo) {}
}
