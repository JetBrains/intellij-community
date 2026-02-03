package com.intellij.terminal.frontend.view.impl

import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent

/**
 * Simple implementation of the [TerminalOutputModelController] that just updates the output model immediately.
 */
internal class TerminalOutputModelControllerImpl(override val model: MutableTerminalOutputModel) : TerminalOutputModelController {
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