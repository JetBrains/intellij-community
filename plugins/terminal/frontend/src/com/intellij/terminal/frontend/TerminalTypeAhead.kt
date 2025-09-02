package com.intellij.terminal.frontend

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener

internal class TerminalTypeAhead(
  private val outputModel: TerminalOutputModel,
) : TerminalShellIntegrationEventsListener {

  @Volatile
  private var isEnabled = false

  @Volatile
  private var promptEndOffset = 0

  override fun promptFinished() {
    isEnabled = true
    promptEndOffset = outputModel.cursorOffsetState.value
  }

  override fun commandStarted(command: String) {
    isEnabled = false
  }

  fun stringTyped(string: String) {
    if (isDisabled()) return
    outputModel.insertAtCursor(string)
  }
  
  fun backspace() {
    if (isDisabled()) return
    if (outputModel.cursorOffsetState.value > promptEndOffset) {
      outputModel.backspace()
    }
  }

  private fun isDisabled() = !Registry.`is`("terminal.type.ahead", false) || !isEnabled
}
