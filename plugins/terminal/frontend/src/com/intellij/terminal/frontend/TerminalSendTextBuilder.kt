package com.intellij.terminal.frontend

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalSendTextBuilder {
  fun shouldExecute(): TerminalSendTextBuilder

  fun useBracketedPasteMode(): TerminalSendTextBuilder

  fun send(text: String)
}