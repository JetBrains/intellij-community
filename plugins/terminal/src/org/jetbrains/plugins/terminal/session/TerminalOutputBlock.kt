// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset

@ApiStatus.Internal
data class TerminalOutputBlock(
  /** Unique zero-based ID of the block */
  val id: Int,
  val startOffset: TerminalOffset,
  val commandStartOffset: TerminalOffset?,
  val outputStartOffset: TerminalOffset?,
  val endOffset: TerminalOffset,
  /**
   * Exit code of the command executed in this block.
   * If code is null, then no command was executed.
   */
  val exitCode: Int?,
)