package org.jetbrains.plugins.terminal.view.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder

@ApiStatus.Internal
class TerminalSendTextBuilderImpl(
  val doSend: (TerminalSendTextOptions) -> Boolean,
) : TerminalSendTextBuilder {
  var shouldExecute: Boolean = false
  var useBracketedPasteMode: Boolean = false
  var requireBracketedPasteMode: Boolean = false
  var sendEndKeyBeforeText: Boolean = false

  override fun shouldExecute(): TerminalSendTextBuilder {
    shouldExecute = true
    return this
  }

  override fun useBracketedPasteMode(): TerminalSendTextBuilder {
    useBracketedPasteMode = true
    return this
  }

  override fun requireBracketedPasteMode(): TerminalSendTextBuilder {
    requireBracketedPasteMode = true
    useBracketedPasteMode = true
    return this
  }

  override fun sendEndKeyBeforeText(): TerminalSendTextBuilder {
    sendEndKeyBeforeText = true
    return this
  }

  override fun send(text: String) {
    trySend(text)
  }

  override fun trySend(text: String): Boolean {
    return doSend(
      TerminalSendTextOptions(
        text = text,
        shouldExecute = shouldExecute,
        useBracketedPasteMode = useBracketedPasteMode,
        requireBracketedPasteMode = requireBracketedPasteMode,
        sendEndKeyBeforeText = sendEndKeyBeforeText,
      )
    )
  }
}

@ApiStatus.Internal
data class TerminalSendTextOptions(
  val text: String,
  val shouldExecute: Boolean,
  val useBracketedPasteMode: Boolean,
  val requireBracketedPasteMode: Boolean = false,
  val sendEndKeyBeforeText: Boolean = false,
)