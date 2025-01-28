// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalState

@ApiStatus.Internal
@Serializable
data class TerminalStateDto(
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

@ApiStatus.Internal
fun TerminalState.toDto(): TerminalStateDto {
  return TerminalStateDto(
    isCursorVisible = isCursorVisible,
    cursorShape = cursorShape,
    mouseMode = mouseMode,
    mouseFormat = mouseFormat,
    isAlternateScreenBuffer = isAlternateScreenBuffer,
    isApplicationArrowKeys = isApplicationArrowKeys,
    isApplicationKeypad = isApplicationKeypad,
    isAutoNewLine = isAutoNewLine,
    isAltSendsEscape = isAltSendsEscape,
    isBracketedPasteMode = isBracketedPasteMode,
    windowTitle = windowTitle,
  )
}

@ApiStatus.Internal
fun TerminalStateDto.toTerminalState(): TerminalState {
  return TerminalState(
    isCursorVisible = isCursorVisible,
    cursorShape = cursorShape,
    mouseMode = mouseMode,
    mouseFormat = mouseFormat,
    isAlternateScreenBuffer = isAlternateScreenBuffer,
    isApplicationArrowKeys = isApplicationArrowKeys,
    isApplicationKeypad = isApplicationKeypad,
    isAutoNewLine = isAutoNewLine,
    isAltSendsEscape = isAltSendsEscape,
    isBracketedPasteMode = isBracketedPasteMode,
    windowTitle = windowTitle
  )
}