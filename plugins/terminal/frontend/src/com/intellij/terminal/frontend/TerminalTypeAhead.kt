package com.intellij.terminal.frontend

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalTypeAhead(
  private val outputModel: TerminalOutputModel,
  private val blocksModel: TerminalBlocksModel,
  private val editor: Editor,
) {
  
  companion object {
    val KEY: Key<TerminalTypeAhead> = Key.create("TerminalTypeAhead")

    private val LOG = logger<TerminalTypeAhead>()
  }

  private fun runUpdate(update: Runnable) {
    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup != null && lookup.editor.isReworkedTerminalEditor) {
      lookup.performGuardedChange(update)
    }
    else {
      update.run()
    }
  }

  fun stringTyped(string: String) {
    if (isDisabled()) return
    runUpdate { outputModel.insertAtCursor(string) }
    LOG.trace { "String typed prediction inserted: '$string'" }
  }
  
  fun backspace() {
    if (isDisabled()) return
    val commandStartOffset = blocksModel.blocks.lastOrNull()?.commandStartOffset
    if (commandStartOffset != null && outputModel.cursorOffsetState.value > commandStartOffset) {
      runUpdate {outputModel.backspace()}
      LOG.trace { "Backspace prediction applied" }
    }
  }

  fun isDisabled() = PlatformUtils.isJetBrainsClient() || isDisabledInRegistry() || !isTypingCommand()

  private fun isDisabledInRegistry(): Boolean = !Registry.`is`("terminal.type.ahead", false)

  private fun isTypingCommand(): Boolean = blocksModel.blocks.lastOrNull()?.let { lastBlock ->
    // The command start offset is where the prompt ends. If it's not there yet, it means the user can't type a command yet.
    // The output start offset is -1 until the command starts executing. Once that happens, it means the user can't type anymore.
    lastBlock.commandStartOffset >= 0 && lastBlock.outputStartOffset == -1
  } == true
}

private fun TerminalOutputModel.insertAtCursor(string: String) {
  val remainingLinePart = getRemainingLinePart()
  if (!remainingLinePart.isBlank()) return // at this moment we only support type-ahead at the end of a visible line
  withTypeAhead {
    val replaceLength = string.length.coerceAtMost(remainingLinePart.length)
    replaceContent(relativeOffset(cursorOffsetState.value), replaceLength, string, emptyList())
    // Do not reuse cursorOffset because replaceContent might change it.
    updateCursorPosition(relativeOffset(cursorOffsetState.value + string.length))
  }
}

private fun TerminalOutputModel.backspace() {
  if (!getRemainingLinePart().isBlank()) return
  val offset = cursorOffsetState.value
  if (offset <= 1) return
  replaceContent(relativeOffset(offset - 1), 1, " ", emptyList()) // false because that's what inline completion expects
}

private fun TerminalOutputModel.getRemainingLinePart(): @NlsSafe String {
  val cursorOffset = cursorOffsetState.value
  val document = document
  val line = document.getLineNumber(cursorOffset)
  val lineEnd = document.getLineEndOffset(line)
  val remainingLinePart = document.getText(TextRange(cursorOffset, lineEnd))
  return remainingLinePart
}
