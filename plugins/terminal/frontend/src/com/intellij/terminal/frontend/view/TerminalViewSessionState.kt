package com.intellij.terminal.frontend.view

import org.jetbrains.annotations.ApiStatus

/**
 * Indicates the state of the terminal session in context of the [TerminalView].
 */
@ApiStatus.Experimental
sealed interface TerminalViewSessionState {
  /**
   * The [TerminalView] component is created, but the terminal session is not started yet.
   */
  object NotStarted : TerminalViewSessionState

  /**
   * The [TerminalView] was connected to the terminal session and can send input and receive output.
   */
  object Running : TerminalViewSessionState

  /**
   * The underlying terminal session was terminated, so the [TerminalView] no more can send input or receive output.
   */
  object Terminated : TerminalViewSessionState
}