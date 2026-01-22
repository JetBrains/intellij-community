package com.intellij.terminal.frontend.view.completion

import com.google.common.base.Ascii
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.frontend.view.impl.TerminalInput
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

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
  val process = TerminalCommandCompletionService.getInstance(lookup.project).activeProcess ?: error("No active completion process")
  val suggestion = item.`object` as ShellCompletionSuggestion
  val typedPrefixLength = lookup.itemPattern(item).length
  val insertionInfo = calculateInsertionInfo(
    process,
    suggestion,
    typedPrefixLength,
    process.beforePrefixReplacementLength,
    process.afterPrefixReplacementLength,
  )
  val optimizedInfo = optimizeInsertionInfo(process.context.outputModel, insertionInfo, typedPrefixLength)

  // First step - remove characters after cursor (if any)
  // Move right and then backspace to delete text after cursor
  if (optimizedInfo.afterPrefixReplacementLength > 0) {
    repeat(optimizedInfo.afterPrefixReplacementLength) {
      terminalInput.sendRight()
    }
    terminalInput.sendBytes(ByteArray(optimizedInfo.afterPrefixReplacementLength) { Ascii.DEL })
  }

  // Second step - remove the typed prefix AND beforePrefixReplacementLength
  val charsToRemove = typedPrefixLength + optimizedInfo.beforePrefixReplacementLength
  if (charsToRemove > 0) {
    terminalInput.sendBytes(ByteArray(charsToRemove) { Ascii.DEL })
  }

  // Third step - insert the completion item
  val realInsertValue = optimizedInfo.insertValue.replace(CURSOR_MARKER, "")
  terminalInput.sendString(realInsertValue)

  // Fourth step - move the cursor to the custom position if it is specified
  val cursorOffset = optimizedInfo.insertValue.indexOf(CURSOR_MARKER)
  if (cursorOffset != -1) {
    val delta = realInsertValue.length - cursorOffset
    repeat(delta) {
      terminalInput.sendLeft()
    }
  }
}

/**
 * Calculates the insertion information taking into account escaping of special characters depending on the shell.
 */
private fun calculateInsertionInfo(
  process: TerminalCommandCompletionProcess,
  suggestion: ShellCompletionSuggestion,
  typedPrefixLength: Int,
  initialBeforeReplacementLength: Int,
  initialAfterReplacementLength: Int,
): CompletionItemInsertionInfo {
  val baseInsertValue = suggestion.insertValue ?: suggestion.name

  if (!suggestion.shouldEscape) {
    // Suggestion provider specified that no escaping is required
    return CompletionItemInsertionInfo(baseInsertValue, initialBeforeReplacementLength, initialAfterReplacementLength)
  }

  if (baseInsertValue.isSurroundedByQuotes()) {
    // Already escaped
    return CompletionItemInsertionInfo(baseInsertValue, initialBeforeReplacementLength, initialAfterReplacementLength)
  }

  val shellName = process.context.shellName
  if (!needsEscaping(shellName, baseInsertValue)) {
    return CompletionItemInsertionInfo(baseInsertValue, initialBeforeReplacementLength, initialAfterReplacementLength)
  }

  val outputModel = process.context.outputModel
  val prefixStartOffset = outputModel.cursorOffset - typedPrefixLength.toLong()
  val tokenStartOffset = prefixStartOffset - suggestion.prefixReplacementIndex.toLong()
  val tokenText = outputModel.getText(tokenStartOffset, prefixStartOffset).toString()

  if (tokenText.startsWith("'") || tokenText.startsWith("\"")) {
    // Token already starts with a quote, so let's insert the token as is.
    return CompletionItemInsertionInfo(baseInsertValue, initialBeforeReplacementLength, initialAfterReplacementLength)
  }

  if (ShellName.isPowerShell(shellName)) {
    // PowerShell prefers escaping by wrapping the token into quotes.
    // Let's wrap the whole token into single quotes.
    val addCursor = !baseInsertValue.contains(CURSOR_MARKER)
    val insertValue = "'${tokenText}${baseInsertValue}${if (addCursor) CURSOR_MARKER else ""}'"
    val beforeReplacementLength = tokenText.length
    val textAfterCursor =
      outputModel.getText(outputModel.cursorOffset, (outputModel.cursorOffset + 1).coerceAtMost(outputModel.endOffset)).toString()
    val afterReplacementLength = if (textAfterCursor == "'" || textAfterCursor == "\"") 1 else 0
    return CompletionItemInsertionInfo(insertValue, beforeReplacementLength, afterReplacementLength)
  }
  else {
    // Unix shells prefer escaping by adding backslashes before the special characters
    val insertValue = buildString {
      val cursorIndex = baseInsertValue.indexOf(CURSOR_MARKER)
      val valueNoCursor = baseInsertValue.replace(CURSOR_MARKER, "")
      for (ind in 0 until valueNoCursor.length) {
        val ch = valueNoCursor[ind]
        if (ind == cursorIndex) {
          append(CURSOR_MARKER)
        }
        if (ch in UNIX_SHELLS_CHARS_TO_ESCAPE) {
          append("\\")
        }
        append(ch)
      }
    }

    return CompletionItemInsertionInfo(insertValue, initialBeforeReplacementLength, initialAfterReplacementLength)
  }
}

private fun optimizeInsertionInfo(
  outputModel: TerminalOutputModel,
  baseInfo: CompletionItemInsertionInfo,
  typedPrefixLength: Int,
): CompletionItemInsertionInfo {
  // It is a safety net for exceptions caused by arithmetic operations mostly.
  return try {
    doOptimizeInsertionInfo(outputModel, baseInfo, typedPrefixLength)
  }
  catch (e: Exception) {
    LOG.error("""
      Failed to optimize completion item insertion, typedPrefixLength: $typedPrefixLength, base info: $baseInfo,
      cursor line context: '${outputModel.getCursorLineContext()}'
    """.trimIndent(), e)
    baseInfo
  }
}

/**
 * Tries to optimize completion item insertion info by reducing the amount of text to replace.
 */
private fun doOptimizeInsertionInfo(
  outputModel: TerminalOutputModel,
  baseInfo: CompletionItemInsertionInfo,
  typedPrefixLength: Int,
): CompletionItemInsertionInfo {
  val cursorOffset = outputModel.cursorOffset
  val prefixStartOffset = cursorOffset - typedPrefixLength.toLong()
  val tokenStartOffset = prefixStartOffset - baseInfo.beforePrefixReplacementLength.toLong()

  val typedTokenText = outputModel.getText(tokenStartOffset, prefixStartOffset).toString()
  val commonPrefixLength = typedTokenText.commonPrefixWith(baseInfo.insertValue).length

  val afterCursorText = outputModel.getText(cursorOffset, cursorOffset + baseInfo.afterPrefixReplacementLength.toLong()).toString()
  val commonSuffixLength = afterCursorText.commonSuffixWith(baseInfo.insertValue).length

  val newBeforeReplacementLength = baseInfo.beforePrefixReplacementLength - commonPrefixLength
  val newAfterReplacementLength = baseInfo.afterPrefixReplacementLength - commonSuffixLength
  val newInsertValue = baseInfo.insertValue.substring(commonPrefixLength, baseInfo.insertValue.length - commonSuffixLength)
  return CompletionItemInsertionInfo(newInsertValue, newBeforeReplacementLength, newAfterReplacementLength)
}

private fun needsEscaping(shellName: ShellName, value: String): Boolean {
  val charsToEscape = if (ShellName.isPowerShell(shellName)) POWERSHELL_CHARS_TO_ESCAPE else UNIX_SHELLS_CHARS_TO_ESCAPE
  return value.replace(CURSOR_MARKER, "").any { it in charsToEscape }
}

/** For debugging purposes */
private fun TerminalOutputModel.getCursorLineContext(): String {
  val cursorLine = getLineByOffset(cursorOffset)
  val lineStartOffset = getStartOfLine(cursorLine)
  val lineEndOffset = getEndOfLine(cursorLine)
  return buildString {
    append(getText(lineStartOffset, lineEndOffset).toString())
    insert((cursorOffset - lineStartOffset).toInt(), "<cursor>")
  }
}

private data class CompletionItemInsertionInfo(
  val insertValue: String,
  val beforePrefixReplacementLength: Int,
  val afterPrefixReplacementLength: Int,
)

private const val CURSOR_MARKER = "{cursor}"

private const val POWERSHELL_CHARS_TO_ESCAPE = " \n\t\r`$'\"(){}[]<>|;&,@#"

private const val UNIX_SHELLS_CHARS_TO_ESCAPE = " \n\t\r`$'\"(){}[]<>|;&*?\\"

private val LOG = fileLogger()