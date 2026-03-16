@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_READ_LIMIT = 2000
private const val MAX_LINE_LENGTH = 500
private const val TAB_WIDTH = 4
private val COMMENT_PREFIXES = listOf("#", "//", "--")
private const val BLOCK_COMMENT_START = "/*"
private const val BLOCK_COMMENT_END = "*/"
private const val ANNOTATION_PREFIX = "@"

private data class IndentationOptions(
  @JvmField val anchorLine: Int,
  @JvmField val maxLevels: Int,
  @JvmField val includeSiblings: Boolean,
  @JvmField val includeHeader: Boolean,
)

private data class LineRecord(
  @JvmField val number: Int,
  @JvmField val raw: String,
  @JvmField val effectiveIndent: Int,
  @JvmField val isHeader: Boolean,
)

internal class ReadToolset : McpToolset {
  /*
   * We intentionally return numbered lines as plain text ("L<line>: ...") instead of JSON.
   * This matches Codex CLI's read_file convention, keeps responses compact for LLMs,
   * and makes line references unambiguous without extra parsing overhead.
   */
  @McpTool
  @McpDescription("""
        Reads a local file and returns numbered lines (1-indexed) as text.
        Modes: slice, lines, line_columns, offsets, indentation.
        Slice uses start_line and max_lines. Lines uses start_line/end_line (inclusive).
        Line_columns uses start_line/start_column and end_line/end_column (end is exclusive; end_line defaults to start_line).
        Offsets uses start_offset/end_offset (end is exclusive). Indentation uses start_line with max_levels/include_*.
        max_lines caps the total output in all modes; context_lines applies to range modes (per side).
    """)
  suspend fun read_file(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    file_path: String,
    @McpDescription("Read mode: 'slice', 'lines', 'line_columns', 'offsets', or 'indentation'")
    mode: String = "slice",
    @McpDescription("1-based line number to start reading from")
    start_line: Int = 1,
    @McpDescription("Maximum number of lines to return (slice uses as line count; all modes cap output)")
    max_lines: Int = DEFAULT_READ_LIMIT,
    @McpDescription("1-based end line for lines/line_columns mode (inclusive for lines; exclusive for line_columns)")
    end_line: Int? = null,
    @McpDescription("1-based start column for line_columns mode")
    start_column: Int? = null,
    @McpDescription("1-based end column for range read (exclusive)")
    end_column: Int? = null,
    @McpDescription("0-based start offset for offsets mode (requires end_offset)")
    start_offset: Int? = null,
    @McpDescription("0-based end offset for offsets mode (exclusive)")
    end_offset: Int? = null,
    @McpDescription("Number of context lines to include around the range (per side)")
    context_lines: Int = 0,
    @McpDescription("Indentation mode: maximum indentation levels to include (0 = only anchor block)")
    max_levels: Int? = null,
    @McpDescription("Indentation mode: include sibling blocks at the same indentation level")
    include_siblings: Boolean? = null,
    @McpDescription("Indentation mode: include header comments/annotations directly above anchor")
    include_header: Boolean? = null,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.reading.file", file_path))
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(file_path)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
               ?: mcpFail("File $resolvedPath doesn't exist or can't be opened")
    val document = readAction {
      if (file.fileType.isBinary) mcpFail("File $resolvedPath is binary")
      FileDocumentManager.getInstance().getDocument(file) ?: mcpFail("Could not get document for $file")
    }

    val normalizedStartLine = requirePositive(start_line, "start_line")
    val normalizedMaxLines = requirePositive(max_lines, "max_lines")
    val normalizedContextLines = requireNonNegativeContextLines(context_lines)
    val normalizedMode = normalizeMode(mode)

    return readAction {
      val result = when (normalizedMode) {
        "slice" -> {
          requireNoRangeParamsForMode("slice", end_line, start_column, end_column, start_offset, end_offset, max_levels, include_siblings, include_header, normalizedContextLines)
          readSlice(document, normalizedStartLine, normalizedMaxLines)
        }
        "lines" -> {
          requireNoLineColumnParams("lines", start_column, end_column)
          requireNoOffsetParams("lines", start_offset, end_offset)
          requireNoIndentationParams("lines", max_levels, include_siblings, include_header)

          val endLine = end_line ?: mcpFail("end_line is required for mode 'lines'")
          if (endLine <= 0) mcpFail("end_line must be > 0")
          if (endLine < normalizedStartLine) mcpFail("end_line must be >= start_line")

          readRangeWithContext(document, normalizedStartLine, endLine, normalizedContextLines, normalizedMaxLines)
        }
        "line_columns" -> {
          requireNoOffsetParams("line_columns", start_offset, end_offset)
          requireNoIndentationParams("line_columns", max_levels, include_siblings, include_header)

          val startColumn = start_column ?: mcpFail("start_column is required for mode 'line_columns'")
          val endColumn = end_column ?: mcpFail("end_column is required for mode 'line_columns'")
          val endLine = end_line ?: normalizedStartLine
          if (endLine < normalizedStartLine) mcpFail("end_line must be >= start_line")

          val startOffset = resolveLineColumnOffset(document, normalizedStartLine, startColumn, "start")
          val endOffset = resolveLineColumnOffset(document, endLine, endColumn, "end")
          if (endOffset < startOffset) mcpFail("end position must be >= start position")

          val inclusiveEndLine = resolveInclusiveEndLine(document, startOffset, endOffset, normalizedStartLine)
          readRangeWithContext(document, normalizedStartLine, inclusiveEndLine, normalizedContextLines, normalizedMaxLines)
        }
        "offsets" -> {
          requireNoLineColumnParams("offsets", start_column, end_column)
          requireNoIndentationParams("offsets", max_levels, include_siblings, include_header)
          if (end_line != null) mcpFail("end_line is not supported in mode 'offsets'")

          val textLength = document.textLength
          val startOffset = start_offset ?: mcpFail("start_offset is required for mode 'offsets'")
          val endOffset = end_offset ?: mcpFail("end_offset is required for mode 'offsets'")
          if (startOffset < 0) mcpFail("start_offset must be >= 0")
          if (endOffset < startOffset) mcpFail("end_offset must be >= start_offset")
          if (endOffset > textLength) mcpFail("end_offset exceeds file length")

          val startLineNumber = document.getLineNumber(startOffset) + 1
          val inclusiveEndLine = resolveInclusiveEndLine(document, startOffset, endOffset, startLineNumber)
          readRangeWithContext(document, startLineNumber, inclusiveEndLine, normalizedContextLines, normalizedMaxLines)
        }
        "indentation" -> {
          requireNoRangeParamsForMode("indentation", end_line, start_column, end_column, start_offset, end_offset, null, null, null, normalizedContextLines)
          val options = resolveIndentationOptions(normalizedStartLine, max_levels, include_siblings, include_header)
          readIndentation(document, normalizedStartLine, normalizedMaxLines, options)
        }
        else -> mcpFail("mode must be one of: slice, lines, line_columns, offsets, indentation")
      }

      val lines = result.lines
      if (lines.isEmpty()) mcpFail("No lines read")
      renderLines(lines)
    }
  }
}

private fun requirePositive(value: Int, name: String): Int {
  if (value <= 0) mcpFail("$name must be > 0")
  return value
}

private fun requireNonNegativeContextLines(value: Int): Int {
  if (value < 0) mcpFail("context_lines must be >= 0")
  return value
}

internal fun normalizeMode(mode: String): String {
  val normalized = mode.trim().lowercase()
  if (normalized.isEmpty()) mcpFail("mode must be one of: slice, lines, line_columns, offsets, indentation")
  return when (normalized) {
    "slice", "lines", "line_columns", "offsets", "indentation" -> normalized
    else -> mcpFail("mode must be one of: slice, lines, line_columns, offsets, indentation")
  }
}

private fun requireNoLineColumnParams(mode: String, startColumn: Int?, endColumn: Int?) {
  if (startColumn != null || endColumn != null) {
    mcpFail("start_column/end_column are not supported in mode '$mode'")
  }
}

private fun requireNoOffsetParams(mode: String, startOffset: Int?, endOffset: Int?) {
  if (startOffset != null || endOffset != null) {
    mcpFail("start_offset/end_offset are not supported in mode '$mode'")
  }
}

private fun requireNoIndentationParams(mode: String, maxLevels: Int?, includeSiblings: Boolean?, includeHeader: Boolean?) {
  if (maxLevels != null || includeSiblings != null || includeHeader != null) {
    mcpFail("max_levels/include_siblings/include_header are not supported in mode '$mode'")
  }
}

private fun requireNoRangeParamsForMode(
  mode: String,
  endLine: Int?,
  startColumn: Int?,
  endColumn: Int?,
  startOffset: Int?,
  endOffset: Int?,
  maxLevels: Int?,
  includeSiblings: Boolean?,
  includeHeader: Boolean?,
  contextLines: Int,
) {
  if (endLine != null) mcpFail("end_line is not supported in mode '$mode'")
  if (startColumn != null || endColumn != null) mcpFail("start_column/end_column are not supported in mode '$mode'")
  if (startOffset != null || endOffset != null) mcpFail("start_offset/end_offset are not supported in mode '$mode'")
  if (contextLines != 0) mcpFail("context_lines is not supported in mode '$mode'")
  if (maxLevels != null || includeSiblings != null || includeHeader != null) {
    mcpFail("max_levels/include_siblings/include_header are only supported in mode 'indentation'")
  }
}

internal fun capContextLines(rangeLines: Int, requestedContext: Int, maxLines: Int): Int {
  if (rangeLines <= 0) mcpFail("range must be greater than zero")
  if (rangeLines > maxLines) {
    mcpFail("range exceeds max_lines; increase max_lines to at least $rangeLines")
  }
  if (requestedContext <= 0) return 0
  val perSideCap = (maxLines - rangeLines) / 2
  return min(requestedContext, perSideCap)
}

private fun readRangeWithContext(
  document: Document,
  startLine: Int,
  inclusiveEndLine: Int,
  contextLines: Int,
  maxLines: Int,
): ReadLinesResult {
  val rangeLines = inclusiveEndLine - startLine + 1
  val contextPerSide = capContextLines(rangeLines, contextLines, maxLines)
  val readStartLine = max(1, startLine - contextPerSide)
  val readEndLine = min(document.lineCount, inclusiveEndLine + contextPerSide)
  return readSlice(document, readStartLine, readEndLine - readStartLine + 1)
}

private fun resolveLineColumnOffset(document: Document, lineNumber: Int, column: Int, label: String): Int {
  if (lineNumber <= 0) mcpFail("$label line must be > 0")
  if (lineNumber > document.lineCount) mcpFail("$label line exceeds file length")
  if (column <= 0) mcpFail("$label column must be > 0")
  val lineIndex = lineNumber - 1
  val lineStart = document.getLineStartOffset(lineIndex)
  val lineEnd = document.getLineEndOffset(lineIndex)
  val maxColumn = lineEnd - lineStart + 1
  if (column > maxColumn) mcpFail("$label column exceeds line length")
  return lineStart + column - 1
}

private fun resolveInclusiveEndLine(document: Document, startOffset: Int, endOffset: Int, fallbackLine: Int): Int {
  return if (endOffset > startOffset) {
    document.getLineNumber(endOffset - 1) + 1
  } else {
    fallbackLine
  }
}

private fun resolveIndentationOptions(
  startLine: Int,
  maxLevels: Int?,
  includeSiblings: Boolean?,
  includeHeader: Boolean?,
): IndentationOptions {
  val normalizedMaxLevels = maxLevels ?: 0
  if (normalizedMaxLevels < 0) mcpFail("max_levels must be >= 0")
  val normalizedIncludeSiblings = includeSiblings ?: false
  val normalizedIncludeHeader = includeHeader ?: true
  return IndentationOptions(anchorLine = startLine,
                            maxLevels = normalizedMaxLevels,
                            includeSiblings = normalizedIncludeSiblings,
                            includeHeader = normalizedIncludeHeader)
}

private data class ReadLine(
  @JvmField val number: Int,
  @JvmField val text: String,
)

private data class ReadLinesResult(
  @JvmField val lines: List<ReadLine>,
)

private fun readSlice(document: Document, startLine: Int, maxLines: Int): ReadLinesResult {
  val lineCount = document.lineCount
  if (startLine > lineCount) mcpFail("start_line exceeds file length")
  val endLine = min(startLine + maxLines - 1, lineCount)
  val output = ArrayList<ReadLine>(endLine - startLine + 1)
  for (lineNumber in startLine..endLine) {
    val rawLine = getLineText(document, lineNumber)
    val display = formatLine(rawLine)
    output.add(ReadLine(number = lineNumber, text = display))
  }
  return ReadLinesResult(lines = output)
}

private fun readIndentation(
  document: Document,
  startLine: Int,
  maxLines: Int,
  options: IndentationOptions,
): ReadLinesResult {
  val anchorLine = options.anchorLine
  if (anchorLine <= 0) mcpFail("start_line must be > 0")
  if (maxLines <= 0) mcpFail("max_lines must be > 0")

  val maxBefore = max(0, maxLines - 1)
  val beforeBuffer = ArrayList<LineRecord>(maxBefore)
  val afterBuffer = ArrayList<LineRecord>(maxBefore)
  var anchorRecord: LineRecord? = null
  var minIndent = 0
  var previousIndent = 0
  var inBlockComment = false
  var seenMinIndent = false

  val lineCount = document.lineCount
  if (anchorLine > lineCount) mcpFail("start_line exceeds file length")

  for (lineNumber in 1..lineCount) {
    val raw = getLineText(document, lineNumber)
    val trimmed = raw.trim()
    val isBlank = trimmed.isEmpty()
    var isHeader = false

    if (!isBlank) {
      if (inBlockComment) {
        isHeader = true
        if (trimmed.contains(BLOCK_COMMENT_END)) {
          inBlockComment = false
        }
      }
      else if (COMMENT_PREFIXES.any { trimmed.startsWith(it) }) {
        isHeader = true
      }
      else if (trimmed.startsWith(BLOCK_COMMENT_START)) {
        isHeader = true
        if (!trimmed.contains(BLOCK_COMMENT_END)) {
          inBlockComment = true
        }
      }
      else if (trimmed.startsWith('*')) {
        isHeader = true
      }
      else if (trimmed.startsWith(ANNOTATION_PREFIX)) {
        isHeader = true
      }
    }

    var indent = previousIndent
    if (!isBlank) {
      indent = measureIndent(raw)
      previousIndent = indent
    }
    val effectiveIndent = indent

    if (lineNumber < anchorLine) {
      if (maxBefore > 0) {
        beforeBuffer.add(LineRecord(number = lineNumber, raw = raw, effectiveIndent = effectiveIndent, isHeader = isHeader))
        if (beforeBuffer.size > maxBefore) {
          beforeBuffer.removeAt(0)
        }
      }
      continue
    }

    if (lineNumber == anchorLine) {
      anchorRecord = LineRecord(number = lineNumber, raw = raw, effectiveIndent = effectiveIndent, isHeader = isHeader)
      minIndent = if (options.maxLevels == 0) 0 else max(0, effectiveIndent - options.maxLevels * TAB_WIDTH)
      if (maxBefore == 0) break
      continue
    }

    if (anchorRecord == null) continue
    if (afterBuffer.size >= maxBefore) break

    if (effectiveIndent < minIndent) {
      break
    }

    if (!options.includeSiblings && effectiveIndent == minIndent) {
      if (seenMinIndent) {
        break
      }
      seenMinIndent = true
    }

    afterBuffer.add(LineRecord(number = lineNumber, raw = raw, effectiveIndent = effectiveIndent, isHeader = isHeader))
    if (afterBuffer.size >= maxBefore) break
  }

  val anchor = anchorRecord ?: mcpFail("start_line exceeds file length")

  var headerRecords = emptyList<LineRecord>()
  if (options.includeHeader && beforeBuffer.isNotEmpty()) {
    var idx = beforeBuffer.size - 1
    while (idx >= 0 && beforeBuffer[idx].isHeader) {
      idx -= 1
    }
    val start = idx + 1
    if (start < beforeBuffer.size) {
      val contiguous = beforeBuffer.subList(start, beforeBuffer.size)
      val maxHeader = max(0, maxLines - 1)
      val takeCount = min(contiguous.size, maxHeader)
      if (takeCount > 0) {
        headerRecords = contiguous.takeLast(takeCount)
        repeat(takeCount) { beforeBuffer.removeAt(beforeBuffer.size - 1) }
      }
    }
  }

  val available = 1 + beforeBuffer.size + afterBuffer.size + headerRecords.size
  val finalLimit = min(maxLines, available)
  if (finalLimit == 1) {
    val lineText = formatLine(anchor.raw)
    return ReadLinesResult(
      lines = listOf(ReadLine(number = anchor.number, text = lineText)),
    )
  }

  var i = beforeBuffer.size - 1
  var j = 0
  var iCounterMinIndent = 0
  var jCounterMinIndent = 0

  val out = if (headerRecords.isNotEmpty()) headerRecords.toMutableList().apply { add(anchor) } else mutableListOf(anchor)

  while (out.size < finalLimit) {
    var progressed = 0

    if (i >= 0) {
      val record = beforeBuffer[i]
      if (record.effectiveIndent >= minIndent) {
        out.add(0, record)
        progressed += 1
        i -= 1

        if (record.effectiveIndent == minIndent && !options.includeSiblings) {
          val allowHeaderLine = options.includeHeader && record.isHeader
          val canTakeLine = allowHeaderLine || iCounterMinIndent == 0
          if (canTakeLine) {
            iCounterMinIndent += 1
          }
          else {
            out.removeAt(0)
            progressed -= 1
            i = -1
          }
        }

        if (out.size >= finalLimit) break
      }
      else {
        i = -1
      }
    }

    if (j < afterBuffer.size) {
      val record = afterBuffer[j]
      if (record.effectiveIndent >= minIndent) {
        out.add(record)
        progressed += 1
        j += 1

        if (record.effectiveIndent == minIndent && !options.includeSiblings) {
          if (jCounterMinIndent > 0) {
            out.removeAt(out.size - 1)
            progressed -= 1
            j = afterBuffer.size
          }
          jCounterMinIndent += 1
        }
      }
      else {
        j = afterBuffer.size
      }
    }

    if (progressed == 0) break
  }

  trimEmptyRecords(out)

  val output = out.map { record ->
    val lineText = formatLine(record.raw)
    ReadLine(number = record.number, text = lineText)
  }
  return ReadLinesResult(lines = output)
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

private fun renderLines(lines: List<ReadLine>): String {
  return lines.joinToString("\n") { line ->
    formatOutputLine(line.number, line.text)
  }
}

private fun formatOutputLine(lineNumber: Int, lineText: String): String {
  return "L$lineNumber: $lineText"
}


private fun measureIndent(line: String): Int {
  var indent = 0
  for (ch in line) {
    when (ch) {
      ' ' -> indent += 1
      '\t' -> indent += TAB_WIDTH
      else -> return indent
    }
  }
  return indent
}

private fun getLineText(document: Document, lineNumber: Int): String {
  val lineIndex = lineNumber - 1
  val start = document.getLineStartOffset(lineIndex)
  val end = document.getLineEndOffset(lineIndex)
  return document.getText(TextRange(start, end))
}

private fun trimEmptyRecords(records: MutableList<LineRecord>) {
  while (records.isNotEmpty() && records.first().raw.trim().isEmpty()) {
    records.removeAt(0)
  }
  while (records.isNotEmpty() && records.last().raw.trim().isEmpty()) {
    records.removeAt(records.lastIndex)
  }
}
