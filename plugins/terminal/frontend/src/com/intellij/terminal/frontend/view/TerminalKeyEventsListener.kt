package com.intellij.terminal.frontend.view

import org.jetbrains.annotations.ApiStatus

/**
 * Listener for the synchronous lifecycle of a terminal key event.
 *
 * The listener methods are called on the EDT synchronously with handling AWT key events.
 * Implementations must return quickly.
 */
@ApiStatus.Experimental
interface TerminalKeyEventsListener {
  /**
   * Called before the event is handled by the terminal.
   *
   * Return `true` when the event is handled and should not be sent to the terminal process.
   */
  fun beforeKeyEvent(event: TerminalKeyEvent): Boolean = false

  /**
   * Called after the terminal finishes handling the event.
   */
  fun afterKeyEvent(event: TerminalKeyEvent) {}
}
