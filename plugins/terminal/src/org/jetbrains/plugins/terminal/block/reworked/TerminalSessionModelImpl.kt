// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.terminal.session.TerminalState
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalSessionModelImpl : TerminalSessionModel {
  private val mutableTerminalStateFlow = MutableStateFlow(getInitialState())
  override val terminalState: StateFlow<TerminalState> = mutableTerminalStateFlow.asStateFlow()

  override fun updateTerminalState(state: TerminalState) {
    mutableTerminalStateFlow.value = state
  }

  private fun getInitialState(): TerminalState {
    return TerminalState(
      isCursorVisible = true,
      cursorShape = null,
      mouseMode = MouseMode.MOUSE_REPORTING_NONE,
      mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM,
      isAlternateScreenBuffer = false,
      isApplicationArrowKeys = false,
      isApplicationKeypad = false,
      isAutoNewLine = false,
      isAltSendsEscape = true,
      isBracketedPasteMode = false,
      windowTitle = "",
      isShellIntegrationEnabled = false,
      currentDirectory = "",
    )
  }
}