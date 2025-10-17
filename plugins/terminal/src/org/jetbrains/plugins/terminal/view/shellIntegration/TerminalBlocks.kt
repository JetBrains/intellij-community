// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalOffset

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalBlockBase {
  val id: TerminalBlockId
  val startOffset: TerminalOffset
  val endOffset: TerminalOffset
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalCommandBlock : TerminalBlockBase {
  val commandStartOffset: TerminalOffset?
  val outputStartOffset: TerminalOffset?
  val exitCode: Int?
}