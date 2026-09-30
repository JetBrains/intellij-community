package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.updateContent

/**
 * Simple implementation of the [TerminalOutputModelController] that just updates the output model immediately.
 */
internal class TerminalOutputModelControllerImpl(
  private val project: Project,
  override val model: MutableTerminalOutputModel,
) : TerminalOutputModelController {
  override fun updateContent(event: TerminalContentUpdatedEvent) {
    updateOutputModel { model.updateContent(event) }
  }

  override fun updateCursorPosition(event: TerminalCursorPositionChangedEvent) {
    updateOutputModel { model.updateCursorPosition(event.logicalLineIndex, event.columnIndex) }
  }

  override fun applyPendingUpdates() {
    // Return immediately, since all updates were applied synchronously
  }

  /** Model changes must not close the active completion lookup of the terminal. */
  private fun updateOutputModel(update: Runnable) {
    // No lookup service means no lookup: do not create the service on the first output.
    val lookup = project.getServiceIfCreated(LookupManager::class.java)?.activeLookup
    if (lookup != null && lookup.topLevelEditor.isReworkedTerminalEditor) {
      lookup.performGuardedChange(update)
    }
    else {
      update.run()
    }
  }
}
