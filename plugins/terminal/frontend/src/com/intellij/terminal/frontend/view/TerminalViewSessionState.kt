package com.intellij.terminal.frontend.view

import org.jetbrains.annotations.ApiStatus

/**
 * Indicates the state of the [TerminalView] connection to the shell process.
 */
@ApiStatus.Experimental
sealed interface TerminalViewSessionState {
  /**
   * The [TerminalView] component is created, but the shell process is not started yet.
   */
  object NotStarted : TerminalViewSessionState

  /**
   * The [TerminalView] was connected to the shell process and can send input and receive output.
   */
  object Running : TerminalViewSessionState

  /**
   * The underlying shell process was terminated, so the [TerminalView] no more can send input or receive output.
   */
  object Terminated : TerminalViewSessionState
}