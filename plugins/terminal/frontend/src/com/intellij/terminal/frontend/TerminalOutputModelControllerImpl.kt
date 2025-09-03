package com.intellij.terminal.frontend

import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.updateContent

/**
 * Simple implementation of the [TerminalOutputModelController] that just updates the output model immediately.
 */
internal class TerminalOutputModelControllerImpl(override val model: TerminalOutputModel) : TerminalOutputModelController {
  override fun updateContent(event: TerminalContentUpdatedEvent) {
    model.updateContent(event)
  }

  override fun updateCursorPosition(event: TerminalCursorPositionChangedEvent) {
    model.updateCursorPosition(event.logicalLineIndex, event.columnIndex)
  }

  override fun applyPendingUpdates() {
    // Return immediately, since all updates were applied synchronously
  }
}