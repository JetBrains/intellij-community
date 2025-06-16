package com.intellij.terminal.frontend

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener

internal class TerminalTypeAhead(
  private val outputModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
) {
  
  companion object {
    val KEY: Key<TerminalTypeAhead> = Key.create("TerminalTypeAhead")
  }

  fun stringTyped(string: String) {
    if (isDisabled()) return
    outputModel.insertAtCursor(string, true)
  }
  
  fun backspace() {
    if (isDisabled()) return
    val commandStartOffset = blocksModel.blocks.lastOrNull()?.commandStartOffset
    if (commandStartOffset != null && outputModel.cursorOffsetState.value > commandStartOffset) {
      outputModel.backspace()
    }
  }

  private fun isDisabled() = isDisabledInRegistry() || !isTypingCommand()

  private fun isDisabledInRegistry(): Boolean = !Registry.`is`("terminal.type.ahead", false)
  
  private fun isTypingCommand(): Boolean = blocksModel.blocks.lastOrNull()?.let { lastBlock ->
    // The command start offset is where the prompt ends. If it's not there yet, it means the user can't type a command yet.
    // The output start offset is -1 until the command starts executing. Once that happens, it means the user can't type anymore.
    lastBlock.commandStartOffset >= 0 && lastBlock.outputStartOffset == -1
  } == true
}
