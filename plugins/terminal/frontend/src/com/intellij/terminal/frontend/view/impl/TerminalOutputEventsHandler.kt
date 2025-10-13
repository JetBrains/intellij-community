package com.intellij.terminal.frontend.view.impl

import org.jetbrains.plugins.terminal.session.TerminalOutputEvent

internal interface TerminalOutputEventsHandler {
  suspend fun handleEvent(event: TerminalOutputEvent)
}