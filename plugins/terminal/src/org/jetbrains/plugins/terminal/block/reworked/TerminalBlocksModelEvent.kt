// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

internal sealed interface TerminalBlocksModelEvent {
  val block: TerminalOutputBlock
}

internal data class TerminalBlockStartedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent

internal data class TerminalBlockFinishedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent

internal data class TerminalBlockRemovedEvent(override val block: TerminalOutputBlock) : TerminalBlocksModelEvent