package com.intellij.terminal.frontend.view.impl

import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.updateContent
import org.jetbrains.plugins.terminal.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.TerminalCursorPositionChangedEvent

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