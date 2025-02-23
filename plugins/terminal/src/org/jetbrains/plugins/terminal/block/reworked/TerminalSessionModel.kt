// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.terminal.session.TerminalState
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalSessionModel {
  val terminalState: StateFlow<TerminalState>

  fun updateTerminalState(state: TerminalState)
}