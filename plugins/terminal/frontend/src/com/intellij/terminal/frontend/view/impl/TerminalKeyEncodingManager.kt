// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.impl

import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.TerminalKeyEncoder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.session.impl.TerminalState

/**
 * Actually a wrapper around [TerminalKeyEncoder].
 * Applies the current terminal state to the encoder on each key encoding.
 */
@ApiStatus.Internal
class TerminalKeyEncodingManager(
  private val sessionModel: TerminalSessionModel,
) {
  // TODO: TerminalKeyEncoder accepts OS platform as a parameter.
  //  which platform should be used there in case of remove dev: frontend or backend?
  // Created on the first key, so the encoder classes do not load when the view opens.
  private val encoder: TerminalKeyEncoder by lazy(LazyThreadSafetyMode.NONE) { TerminalKeyEncoder() }

  private var curEncodingState: EncodingState? = null

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun getCode(key: Int, modifiers: Int): ByteArray? {
    val newEncodingState = sessionModel.terminalState.value.toEncodingState()
    if (curEncodingState != newEncodingState) {
      curEncodingState = newEncodingState
      applyEncodingState(newEncodingState)
    }
    return encoder.getCode(key, modifiers)
  }

  private fun applyEncodingState(state: EncodingState) {
    if (state.isApplicationArrowKeys) {
      encoder.arrowKeysApplicationSequences()
    }
    else encoder.arrowKeysAnsiCursorSequences()

    if (state.isApplicationKeypad) {
      encoder.keypadApplicationSequences()
    }
    else encoder.keypadAnsiSequences()

    encoder.setAutoNewLine(state.isAutoNewLine)
    encoder.setAltSendsEscape(state.isAltSendsEscape)
  }

  private fun TerminalState.toEncodingState(): EncodingState {
    return EncodingState(
      isApplicationArrowKeys = isApplicationArrowKeys,
      isApplicationKeypad = isApplicationKeypad,
      isAutoNewLine = isAutoNewLine,
      isAltSendsEscape = isAltSendsEscape
    )
  }

  private data class EncodingState(
    val isApplicationArrowKeys: Boolean,
    val isApplicationKeypad: Boolean,
    val isAutoNewLine: Boolean,
    val isAltSendsEscape: Boolean,
  )
}
