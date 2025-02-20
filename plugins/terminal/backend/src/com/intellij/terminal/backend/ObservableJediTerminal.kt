// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.util.EventDispatcher
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer

/**
 * Some methods of [JediTerminal] are only modifying its internal state.
 * And it is not possible to be notified when it changes.
 * This wrapper overrides some of these methods and allows to listen for state changes.
 */
internal class ObservableJediTerminal(
  terminalDisplay: TerminalDisplay,
  textBuffer: TerminalTextBuffer,
  initialStyleState: StyleState,
) : JediTerminal(terminalDisplay, textBuffer, initialStyleState) {
  private val dispatcher = EventDispatcher.create(JediTerminalListener::class.java)

  var applicationArrowKeys: Boolean = false
    private set
  var applicationKeypad: Boolean = false
    private set
  var altSendsEscape: Boolean = true
    private set
  var alternativeBufferEnabled: Boolean = false
    private set

  fun addListener(listener: JediTerminalListener) {
    dispatcher.addListener(listener)
  }

  fun removeListener(listener: JediTerminalListener) {
    dispatcher.removeListener(listener)
  }

  override fun setApplicationArrowKeys(enabled: Boolean) {
    super.setApplicationArrowKeys(enabled)
    if (applicationArrowKeys != enabled) {
      applicationArrowKeys = enabled
      dispatcher.multicaster.arrowKeysModeChanged(enabled)
    }
  }

  override fun setApplicationKeypad(enabled: Boolean) {
    super.setApplicationKeypad(enabled)
    if (applicationKeypad != enabled) {
      applicationKeypad = enabled
      dispatcher.multicaster.keypadModeChanged(enabled)
    }
  }

  override fun setAutoNewLine(enabled: Boolean) {
    val autoNewLine = isAutoNewLine
    super.setAutoNewLine(enabled)
    if (autoNewLine != enabled) {
      dispatcher.multicaster.autoNewLineChanged(enabled)
    }
  }

  override fun setAltSendsEscape(enabled: Boolean) {
    super.setAltSendsEscape(enabled)
    if (altSendsEscape != enabled) {
      altSendsEscape = enabled
      dispatcher.multicaster.altSendsEscapeChanged(enabled)
    }
  }

  override fun useAlternateBuffer(enabled: Boolean) {
    if (alternativeBufferEnabled != enabled) {
      alternativeBufferEnabled = enabled
      dispatcher.multicaster.beforeAlternateScreenBufferChanged(enabled)
    }
    super.useAlternateBuffer(enabled)
  }
}