// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface TerminalBlockBase {
  val id: TerminalBlockId
  val startOffset: TerminalOffset
  val endOffset: TerminalOffset
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface TerminalCommandBlock : TerminalBlockBase {
  val commandStartOffset: TerminalOffset?
  val outputStartOffset: TerminalOffset?
  val exitCode: Int?
}