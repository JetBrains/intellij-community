// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import org.jetbrains.plugins.terminal.session.impl.TerminalState
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.toDto

/**
 * Tracks [TerminalState] changes and provides updates only when the state has actually changed since the last request.
 *
 * Thread safety: all access must be under [com.jediterm.terminal.model.TerminalTextBuffer] lock.
 */
internal class TerminalStateChangesTracker(initialState: TerminalState) {
  private var currentState: TerminalState = initialState
  private var lastSentState: TerminalState = initialState

  /** Update state lazily. The change will be picked up by the next [getStateUpdate] call. */
  inline fun updateState(updater: (TerminalState) -> TerminalState) {
    currentState = updater(currentState)
  }

  /** Returns a state event if state changed since last request, null otherwise. */
  fun getStateUpdate(): TerminalStateChangedEvent? {
    val current = currentState
    if (current != lastSentState) {
      lastSentState = current
      return TerminalStateChangedEvent(current.toDto())
    }
    return null
  }
}