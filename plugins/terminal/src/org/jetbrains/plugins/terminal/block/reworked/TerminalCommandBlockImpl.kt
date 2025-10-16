// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalCommandBlockImpl(
  override val id: TerminalBlockId,
  override val startOffset: TerminalOffset,
  override val endOffset: TerminalOffset,
  override val commandStartOffset: TerminalOffset?,
  override val outputStartOffset: TerminalOffset?,
  override val exitCode: Int?,
) : TerminalCommandBlock