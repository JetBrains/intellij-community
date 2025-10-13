// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalShellIntegration {
  val blocksModel: TerminalBlocksModel

  // todo
}