@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.isUnderProjectDirectory
import com.intellij.mcpserver.util.resolveReadFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private const val DEFAULT_READ_LIMIT = 2000
private const val MAX_READ_LIMIT = 5000
private const val MAX_LINE_LENGTH = 2000

internal class ReadToolset : McpToolset {
  /*
   * We intentionally return numbered lines as plain text ("L<line>: ...") instead of JSON.
   * This matches Codex CLI's read_file convention, keeps responses compact for LLMs,
   * and makes line references unambiguous without extra parsing overhead.
   */
  @McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
        Reads a file in the project directory or from any project dependency or other project source root.
        Can read sources inside Jar/Jrt files and decompile Java class files inside Jar/Jrt files or on disk. 
        Returns numbered lines (1-indexed) as text.
        By default, reads up to $DEFAULT_READ_LIMIT lines starting from the beginning of the file.
        The maximum accepted limit is $MAX_READ_LIMIT lines.
    """)
  suspend fun read_file(
    @McpDescription(Constants.FILE_PATH_DESCRIPTION)
    file_path: String,
    @McpDescription("1-based line number to start reading from")
    offset: Int = 1,
    @McpDescription("Maximum number of lines to return (default: $DEFAULT_READ_LIMIT, max: $MAX_READ_LIMIT)")
    limit: Int = DEFAULT_READ_LIMIT,
  ): String {
    val normalizedOffset = requirePositive(offset, "offset")
    val normalizedLimit = normalizeReadLimit(limit)

    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.reading.file", file_path))
    val project = currentCoroutineContext().project
    val file = withContext(Dispatchers.IO) { resolveReadFile(project, file_path) }
    val isUnderProjectDirectory = isUnderProjectDirectory(project, file)

    val fileDocumentManager = serviceAsync<FileDocumentManager>()
    val fileIndex = project.serviceAsync<ProjectRootManager>().fileIndex
    return readAction {
      if (!(isUnderProjectDirectory || fileIndex.isInProjectOrExcluded(file))) {
        mcpFail("File $file_path is outside project, library, and SDK roots")
      }
      val document = fileDocumentManager.getDocument(file, project) ?: mcpFail("Could not get document for $file")
      renderSlice(document, normalizedOffset, normalizedLimit)
    }
  }
}

private fun requirePositive(value: Int, name: String): Int {
  if (value <= 0) mcpFail("$name must be > 0")
  return value
}

private fun normalizeReadLimit(limit: Int): Int {
  val normalizedLimit = requirePositive(limit, "limit")
  if (normalizedLimit > MAX_READ_LIMIT) mcpFail("limit must be <= $MAX_READ_LIMIT")
  return normalizedLimit
}

private fun renderSlice(document: Document, startLine: Int, maxLines: Int): String {
  val lineCount = document.lineCount
  if (lineCount == 0) {
    if (startLine > 1) mcpFail("offset exceeds file length")
    return "L1: "
  }
  if (startLine > lineCount) mcpFail("offset exceeds file length")
  val availableLines = lineCount - startLine + 1

  if (availableLines <= maxLines) {
    return buildLineRange(document, startLine, lineCount)
  }

  // codex-style head+tail truncation: keep first half and last half of the budget,
  // replace the middle with a truncation marker.
  val headCount = maxLines / 2
  val tailCount = maxLines - headCount
  val headEnd = startLine + headCount - 1
  val tailStart = lineCount - tailCount + 1
  val truncatedCount = tailStart - headEnd - 1

  val output = StringBuilder()
  if (headCount > 0) {
    output.append(buildLineRange(document, startLine, headEnd))
    output.append('\n')
  }
  output.append("\u2026${truncatedCount} lines truncated\u2026")
  if (tailCount > 0) {
    output.append('\n')
    output.append(buildLineRange(document, tailStart, lineCount))
  }
  return output.toString()
}

private fun buildLineRange(document: Document, startLine: Int, endLine: Int): String {
  val output = StringBuilder()
  for (lineNumber in startLine..endLine) {
    if (output.isNotEmpty()) output.append('\n')
    output.append('L').append(lineNumber).append(": ").append(formatLine(getLineText(document, lineNumber)))
  }
  return output.toString()
}

private fun formatLine(line: String): String {
  if (line.length <= MAX_LINE_LENGTH) return line
  val boundaryIndex = MAX_LINE_LENGTH - 1
  val boundaryChar = line[boundaryIndex]
  return if (Character.isHighSurrogate(boundaryChar)) {
    takeCodePoints(line)
  }
  else {
    line.substring(0, MAX_LINE_LENGTH)
  }
}

private fun takeCodePoints(text: String): String {
  val sb = StringBuilder()
  var i = 0
  var count = 0
  while (i < text.length && count < MAX_LINE_LENGTH) {
    val ch = text[i]
    if (Character.isHighSurrogate(ch) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
      sb.append(ch)
      sb.append(text[i + 1])
      i += 2
    }
    else {
      sb.append(ch)
      i += 1
    }
    count += 1
  }
  return sb.toString()
}

private fun getLineText(document: Document, lineNumber: Int): String {
  val lineIndex = lineNumber - 1
  val start = document.getLineStartOffset(lineIndex)
  val end = document.getLineEndOffset(lineIndex)
  return document.getText(TextRange(start, end))
}
