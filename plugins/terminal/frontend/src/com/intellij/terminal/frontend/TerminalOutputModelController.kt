package com.intellij.terminal.frontend

import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalCursorPositionChangedEvent
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel

/**
 * A controller for the output model updates.
 * Any changes to the model should be done through this controller (except for the initial content).
 * Note that it is not guaranteed that the model will be updated immediately, it is up to the implementation.
 */
internal interface TerminalOutputModelController {
  val model: TerminalOutputModel

  @RequiresEdt
  fun updateContent(event: TerminalContentUpdatedEvent)

  @RequiresEdt
  fun updateCursorPosition(event: TerminalCursorPositionChangedEvent)

  /**
   * Forces the controller to apply all pending updates if they were delayed.
   * After this method returns, it is guaranteed that all previous updates are applied to the model.
   */
  @RequiresEdt
  fun applyPendingUpdates()
}