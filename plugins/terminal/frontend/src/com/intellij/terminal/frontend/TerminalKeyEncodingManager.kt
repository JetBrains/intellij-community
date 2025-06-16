// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.terminal.session.TerminalState
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.TerminalKeyEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel

/**
 * Actually a wrapper around [com.jediterm.terminal.TerminalKeyEncoder].
 * Listens for terminal state changes and updates keys encoding.
 */
internal class TerminalKeyEncodingManager(
  private val sessionModel: TerminalSessionModel,
  coroutineScope: CoroutineScope,
) {
  // TODO: TerminalKeyEncoder accepts OS platform as a parameter.
  //  which platform should be used there in case of remove dev: frontend or backend?
  private val encoder: TerminalKeyEncoder = TerminalKeyEncoder()

  init {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      var curEncodingState: EncodingState? = null

      sessionModel.terminalState.collect { terminalState ->
        val newEncodingState = terminalState.toEncodingState()
        if (curEncodingState != newEncodingState) {
          curEncodingState = newEncodingState
          applyEncodingState(newEncodingState)
        }
      }
    }
  }

  @RequiresEdt
  fun getCode(key: Int, modifiers: Int): ByteArray? {
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