package com.intellij.terminal.frontend.view.impl

import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent

internal interface TerminalOutputEventsHandler {
  suspend fun handleEvent(event: TerminalOutputEvent)
}