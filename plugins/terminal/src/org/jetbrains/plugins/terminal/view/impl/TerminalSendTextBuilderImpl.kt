package org.jetbrains.plugins.terminal.view.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder

@ApiStatus.Internal
class TerminalSendTextBuilderImpl(
  val doSend: (TerminalSendTextOptions) -> Unit,
) : TerminalSendTextBuilder {
  var shouldExecute: Boolean = false
  var useBracketedPasteMode: Boolean = false

  override fun shouldExecute(): TerminalSendTextBuilder {
    shouldExecute = true
    return this
  }

  override fun useBracketedPasteMode(): TerminalSendTextBuilder {
    useBracketedPasteMode = true
    return this
  }

  override fun send(text: String) {
    doSend(TerminalSendTextOptions(text, shouldExecute, useBracketedPasteMode))
  }
}

@ApiStatus.Internal
data class TerminalSendTextOptions(
  val text: String,
  val shouldExecute: Boolean,
  val useBracketedPasteMode: Boolean,
)