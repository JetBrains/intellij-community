package com.intellij.mcpserver.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable

@Serializable
enum class TruncateMode {
  START, MIDDLE, END, NONE
}

const val truncatedMarker: String = "<<<...content truncated...>>>"
const val maxTextLength: Int = 64 * 1024

fun truncateText(text: String, maxLinesCount: Int, maxTextLength: Int = com.intellij.mcpserver.util.maxTextLength, truncateMode: TruncateMode = TruncateMode.START, truncatedMarker: String = com.intellij.mcpserver.util.truncatedMarker): String {
  require(maxLinesCount > 2) { "maxLinesCount must be greater than 2" }
  val lines = text.split("\n")
  val truncatedByLinesText = if (lines.size <= maxLinesCount) {
    text
  }
  else {
    when (truncateMode) {
      TruncateMode.START -> (lines.take(maxLinesCount - 1) + truncatedMarker).joinToString("\n")
      TruncateMode.END -> (listOf(truncatedMarker) + lines.takeLast(maxLinesCount - 1)).joinToString("\n")
      TruncateMode.MIDDLE -> {
        val startLines = lines.take(maxLinesCount / 2)
        val endLines = lines.takeLast(maxLinesCount / 2 - 1)
        val strings = startLines + truncatedMarker + endLines
        strings.joinToString("\n")
      }
      else -> text
    }
  }
  if (truncatedByLinesText.length <= maxTextLength) {
    return truncatedByLinesText
  }
  else { // can be longer than maxTextLength, but it's better returning only the marker if maxTextLength is smaller than the marker length
    return truncatedByLinesText.take(maxTextLength) + truncatedMarker
  }
}

fun Document.getWholeLinesTextRange(linesRange: IntRange): TextRange {
  val startOffset = getLineStartOffset(linesRange.first.coerceIn(0, lineCount - 1))
  val endOffset = getLineEndOffset(linesRange.last.coerceIn(0, lineCount - 1))
  return TextRange(startOffset, endOffset)
}

fun Document.getWholeLinesTextRange(range: TextRange): TextRange {
  val startLine = getLineNumber(range.startOffset)
  val endLine = getLineNumber(range.endOffset)
  return getWholeLinesTextRange(startLine..endLine)
}