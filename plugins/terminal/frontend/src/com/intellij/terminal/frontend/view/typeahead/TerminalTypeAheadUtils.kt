package com.intellij.terminal.frontend.view.typeahead

import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent

internal fun TerminalContentUpdatedEvent.findLineStartOffset(relativeLineIndex: Long): Int? {
  var offset = 0
  repeat(relativeLineIndex.toInt()) {
    val nextNewline = text.indexOf('\n', offset)
    if (nextNewline == -1) return null
    offset = nextNewline + 1
  }
  return offset
}