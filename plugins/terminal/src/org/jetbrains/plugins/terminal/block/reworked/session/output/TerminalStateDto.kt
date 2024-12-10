// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.output

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode

internal data class TerminalStateDto(
  val isCursorVisible: Boolean,
  val cursorShape: CursorShape?,
  val mouseMode: MouseMode,
  val mouseFormat: MouseFormat,
  val isAlternateScreenBuffer: Boolean,
  val isApplicationArrowKeys: Boolean,
  val isApplicationKeypad: Boolean,
  val isAutoNewLine: Boolean,
  val isAltSendsEscape: Boolean,
  val isBracketedPasteMode: Boolean,
  val windowTitle: String,
)