package com.intellij.terminal.frontend.view.typeahead

import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

internal data class TerminalLogicalPosition(
  val lineIndex: Long,
  val columnIndex: Int,
)

internal fun TerminalOutputModel.getCursorPosition(): TerminalLogicalPosition {
  val cursorOffset = cursorOffset
  val lineIndex = getLineByOffset(cursorOffset)
  val cursorColumn = cursorOffset - getStartOfLine(lineIndex)
  return TerminalLogicalPosition(lineIndex.toAbsolute(), cursorColumn.toInt())
}

internal fun TerminalOutputModel.logicalPositionToOffset(position: TerminalLogicalPosition): TerminalOffset {
  return getStartOfLine(TerminalLineIndex.of(position.lineIndex)) + position.columnIndex.toLong()
}