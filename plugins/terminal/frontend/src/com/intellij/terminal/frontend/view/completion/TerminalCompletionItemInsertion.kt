package com.intellij.terminal.frontend.view.completion

import com.google.common.base.Ascii
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.frontend.view.impl.TerminalInput

/**
 * Sends the data to the [TerminalInput] to properly insert the completion item.
 * Does not modify the terminal output model directly.
 * It will be updated when shell handles the input and updates the screen text.
 */
internal fun insertTerminalCompletionItem(
  lookup: LookupImpl,
  item: LookupElement,
) {
  val terminalInput = lookup.editor.getUserData(TerminalInput.KEY) ?: error("No TerminalInput found in the editor")

  // Get the active process to retrieve replacement lengths
  val process = TerminalCommandCompletionService.getInstance(lookup.project).activeProcess
  val beforeReplacementLength = process?.beforePrefixReplacementLength ?: 0
  val afterReplacementLength = process?.afterPrefixReplacementLength ?: 0

  // First step - remove characters after cursor (if any)
  // Move right and then backspace to delete text after cursor
  if (afterReplacementLength > 0) {
    repeat(afterReplacementLength) {
      terminalInput.sendRight()
    }
    terminalInput.sendBytes(ByteArray(afterReplacementLength) { Ascii.DEL })
  }

  // Second step - remove the typed prefix AND beforePrefixReplacementLength
  val typedPrefixLength = lookup.itemPattern(item).length
  val charsToRemove = typedPrefixLength + beforeReplacementLength
  if (charsToRemove > 0) {
    terminalInput.sendBytes(ByteArray(charsToRemove) { Ascii.DEL })
  }

  // Third step - insert the completion item
  val suggestion = item.`object` as ShellCompletionSuggestion
  val realInsertValue = suggestion.insertValue?.replace("{cursor}", "") ?: suggestion.name
  terminalInput.sendString(realInsertValue)

  // Fourth step - move the cursor to the custom position if it is specified
  val cursorOffset = suggestion.insertValue?.indexOf("{cursor}")
  if (cursorOffset != null && cursorOffset != -1) {
    val delta = realInsertValue.length - cursorOffset
    repeat(delta) {
      terminalInput.sendLeft()
    }
  }
}