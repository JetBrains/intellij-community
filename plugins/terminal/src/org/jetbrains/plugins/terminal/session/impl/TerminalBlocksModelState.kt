// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockBase

@ApiStatus.Internal
data class TerminalBlocksModelState(
  val blocks: List<TerminalBlockBase>,
  val blockIdCounter: Int,
)