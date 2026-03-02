// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.mcpFail

private const val BEGIN_MARKER = "*** Begin Patch"
private const val END_MARKER = "*** End Patch"
private const val ADD_PREFIX = "*** Add File: "
private const val UPDATE_PREFIX = "*** Update File: "
private const val DELETE_PREFIX = "*** Delete File: "
private const val MOVE_PREFIX = "*** Move to: "
private const val END_OF_FILE = "*** End of File"

private val HEREDOC_PREFIXES = setOf("<<EOF", "<<'EOF'", "<<\"EOF\"")
private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val ESCAPED_CONTROL_REGEX = Regex("\\\\[nrt]")

internal sealed interface PatchOperation {
  val path: String
}

internal data class AddPatchOperation(
  override val path: String,
  val content: String,
) : PatchOperation

internal data class DeletePatchOperation(
  override val path: String,
) : PatchOperation

internal data class PatchHunkLine(
  val prefix: Char,
  val text: String,
)

internal data class PatchHunk(
  val header: String?,
  val lines: List<PatchHunkLine>,
  val isEndOfFile: Boolean,
)

internal data class UpdatePatchOperation(
  override val path: String,
  val moveTo: String?,
  val hunks: List<PatchHunk>,
) : PatchOperation

internal object PatchApplyEngine {
  fun extractPatchText(input: String?, patch: String?): String {
    if (input != null) return input
    if (patch != null) return patch
    mcpFail("input must be a non-empty string")
  }

  fun parsePatch(text: String): List<PatchOperation> {
    val lines = unwrapHeredocLines(splitLines(text))
    val startIndex = findMarkerIndex(lines, BEGIN_MARKER)
    if (startIndex < 0) mcpFail("patch must include *** Begin Patch")

    val endIndex = findMarkerIndex(lines, END_MARKER, startIndex + 1)
    if (endIndex < 0) mcpFail("patch must include *** End Patch")

    val operations = mutableListOf<PatchOperation>()
    var index = startIndex + 1
    while (index < endIndex) {
      val line = lines[index]
      val headerLine = line.trimStart()

      when {
        headerLine.startsWith(ADD_PREFIX) -> {
          val path = headerLine.removePrefix(ADD_PREFIX).trim()
          if (path.isEmpty()) mcpFail("Add File requires a path")
          ensureSafePatchPath(path, "Add File")

          index += 1
          val contentLines = mutableListOf<String>()
          while (index < endIndex && !isPatchHeaderLine(lines[index])) {
            val contentLine = lines[index]
            if (!contentLine.startsWith('+')) mcpFail("Add File lines must start with +")
            contentLines += contentLine.substring(1)
            index += 1
          }

          val content = if (contentLines.isEmpty()) "" else contentLines.joinToString("\n", postfix = "\n")
          operations += AddPatchOperation(path = path, content = content)
        }

        headerLine.startsWith(DELETE_PREFIX) -> {
          val path = headerLine.removePrefix(DELETE_PREFIX).trim()
          if (path.isEmpty()) mcpFail("Delete File requires a path")
          ensureSafePatchPath(path, "Delete File")

          operations += DeletePatchOperation(path)
          index += 1
        }

        headerLine.startsWith(UPDATE_PREFIX) -> {
          val path = headerLine.removePrefix(UPDATE_PREFIX).trim()
          if (path.isEmpty()) mcpFail("Update File requires a path")
          ensureSafePatchPath(path, "Update File")
          index += 1

          var moveTo: String? = null
          if (index < endIndex && isPatchHeaderLine(lines[index])) {
            val moveLine = lines[index].trimStart()
            if (moveLine.startsWith(MOVE_PREFIX)) {
              moveTo = moveLine.removePrefix(MOVE_PREFIX).trim()
              if (moveTo.isEmpty()) mcpFail("Move to requires a path")
              ensureSafePatchPath(moveTo, "Move to")
              index += 1
            }
          }

          val hunks = mutableListOf<PatchHunk>()
          while (index < endIndex && !isPatchHeaderLine(lines[index])) {
            if (lines[index].trim().isEmpty()) {
              index += 1
              continue
            }

            var header: String? = null
            if (isHunkHeaderLine(lines[index])) {
              val trimmed = lines[index].trim()
              val headerText = if (trimmed.length > 2) trimmed.substring(2).trim() else ""
              header = headerText.ifEmpty { null }
              index += 1
            }
            else if (hunks.isEmpty()) {
              if (!isDiffLine(lines[index])) mcpFail("Expected @@ hunk header")
            }
            else {
              mcpFail("Expected @@ hunk header")
            }

            val hunkLines = mutableListOf<PatchHunkLine>()
            var isEndOfFile = false
            while (index < endIndex && !isHunkHeaderLine(lines[index]) && !isPatchHeaderLine(lines[index])) {
              val hunkLine = lines[index]
              if (hunkLine == END_OF_FILE) {
                isEndOfFile = true
                index += 1
                break
              }

              if (hunkLine.isEmpty()) {
                hunkLines += PatchHunkLine(prefix = ' ', text = "")
                index += 1
                continue
              }

              val prefix = hunkLine.first()
              if (!isDiffPrefix(prefix)) {
                if (hunkLines.isEmpty()) mcpFail("Hunk lines must start with space, +, or -")
                break
              }

              hunkLines += PatchHunkLine(prefix = prefix, text = hunkLine.substring(1))
              index += 1
            }

            if (hunkLines.isEmpty()) mcpFail("Empty hunk in Update File")
            hunks += PatchHunk(header = header, lines = hunkLines, isEndOfFile = isEndOfFile)
          }

          if (hunks.isEmpty()) mcpFail("Update File requires at least one hunk")
          operations += UpdatePatchOperation(path = path, moveTo = moveTo, hunks = hunks)
        }

        line.trim().isEmpty() -> {
          index += 1
        }

        else -> mcpFail("Unexpected patch line: $line")
      }
    }

    if (operations.isEmpty()) mcpFail("patch did not contain any operations")
    return operations
  }

  fun applyHunks(originalText: String, hunks: List<PatchHunk>): String {
    val content = splitLines(originalText).toMutableList()
    var searchStart = 0

    for (hunk in hunks) {
      if (hunk.header != null) {
        val headerIndex = findSequence(content, listOf(hunk.header), searchStart, preferEnd = false)
        if (headerIndex < 0) mcpFail("Hunk context not found")
        searchStart = headerIndex + 1
      }

      val (oldLines, newLines) = buildHunkLines(hunk.lines)
      if (oldLines.isEmpty()) {
        val insertionIndex = content.size
        content.addAll(insertionIndex, newLines)
        searchStart = insertionIndex + newLines.size
        continue
      }

      var matchedIndex = findSequence(content, oldLines, searchStart, hunk.isEndOfFile)
      if (matchedIndex < 0 && searchStart > 0 && !hunk.isEndOfFile) {
        matchedIndex = findSequence(content, oldLines, 0, preferEnd = false)
      }
      if (matchedIndex < 0) mcpFail("Hunk context not found")

      content.subList(matchedIndex, matchedIndex + oldLines.size).clear()
      content.addAll(matchedIndex, newLines)
      searchStart = matchedIndex + newLines.size
    }

    if (content.isNotEmpty() && content.last().isNotEmpty()) {
      content += ""
    }

    return content.joinToString("\n")
  }

}

private fun buildHunkLines(lines: List<PatchHunkLine>): Pair<List<String>, List<String>> {
  val oldLines = mutableListOf<String>()
  val newLines = mutableListOf<String>()

  for (line in lines) {
    when (line.prefix) {
      ' ' -> {
        oldLines += line.text
        newLines += line.text
      }

      '-' -> oldLines += line.text
      '+' -> newLines += line.text
    }
  }

  return oldLines to newLines
}

private fun findSequence(
  haystack: List<String>,
  needle: List<String>,
  startIndex: Int = 0,
  preferEnd: Boolean = false,
): Int {
  if (needle.isEmpty()) return startIndex
  if (needle.size > haystack.size) return -1

  val maxStart = haystack.size - needle.size
  val searchStart = if (preferEnd) maxStart else maxOf(0, startIndex)
  if (searchStart > maxStart) return -1

  searchExact(haystack, needle, searchStart, maxStart).takeIf { it >= 0 }?.let { return it }
  searchWithTransform(haystack, needle, searchStart, maxStart, String::trimEnd).takeIf { it >= 0 }?.let { return it }
  searchWithTransform(haystack, needle, searchStart, maxStart, String::trim).takeIf { it >= 0 }?.let { return it }
  return searchWithTransform(haystack, needle, searchStart, maxStart, ::normalizeForMatch)
}

private fun searchExact(
  haystack: List<String>,
  needle: List<String>,
  searchStart: Int,
  maxStart: Int,
): Int {
  val firstNeedleLine = needle.first()
  for (haystackIndex in searchStart..maxStart) {
    if (haystack[haystackIndex] != firstNeedleLine) continue

    var isMatch = true
    for (needleIndex in 1 until needle.size) {
      if (haystack[haystackIndex + needleIndex] != needle[needleIndex]) {
        isMatch = false
        break
      }
    }
    if (isMatch) return haystackIndex
  }
  return -1
}

private fun searchWithTransform(
  haystack: List<String>,
  needle: List<String>,
  searchStart: Int,
  maxStart: Int,
  transform: (String) -> String,
): Int {
  val transformedNeedle = ArrayList<String>(needle.size)
  for (needleLine in needle) {
    transformedNeedle += transform(needleLine)
  }
  val firstNeedleLine = transformedNeedle.first()

  val transformedHaystack = HashMap<Int, String>()
  fun transformedHaystackLine(index: Int): String {
    return transformedHaystack.getOrPut(index) { transform(haystack[index]) }
  }

  for (haystackIndex in searchStart..maxStart) {
    if (transformedHaystackLine(haystackIndex) != firstNeedleLine) continue

    var isMatch = true
    for (needleIndex in 1 until transformedNeedle.size) {
      if (transformedHaystackLine(haystackIndex + needleIndex) != transformedNeedle[needleIndex]) {
        isMatch = false
        break
      }
    }
    if (isMatch) return haystackIndex
  }

  return -1
}

private fun normalizeForMatch(text: String): String {
  return buildString {
    for (char in text.trim()) {
      append(normalizeCharacter(char))
    }
  }
}

private fun normalizeCharacter(char: Char): Char {
  return when (char) {
    '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> '-'
    '\u2018', '\u2019', '\u201A', '\u201B' -> '\''
    '\u201C', '\u201D', '\u201E', '\u201F' -> '"'
    '\u00A0', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u202F', '\u205F', '\u3000' -> ' '
    else -> char
  }
}

private fun isPatchHeaderLine(line: String): Boolean {
  if (line.isEmpty() || isDiffPrefix(line.first())) return false
  val trimmed = line.trimStart()
  if (trimmed == END_OF_FILE) return false
  return trimmed.startsWith("*** ")
}

private fun isHunkHeaderLine(line: String): Boolean {
  if (line.isEmpty() || isDiffPrefix(line.first())) return false
  return line.trimStart().startsWith("@@")
}

private fun isDiffLine(line: String): Boolean {
  if (line.isEmpty()) return true
  return isDiffPrefix(line.first())
}

private fun isDiffPrefix(prefix: Char): Boolean {
  return prefix == ' ' || prefix == '+' || prefix == '-'
}

private fun unwrapHeredocLines(lines: List<String>): List<String> {
  if (lines.size < 4) return lines

  val first = lines.first().trim()
  val last = lines.last().trim()
  if (first !in HEREDOC_PREFIXES || !last.endsWith("EOF")) return lines
  return lines.subList(1, lines.size - 1)
}

private fun findMarkerIndex(lines: List<String>, marker: String, start: Int = 0): Int {
  for (index in start until lines.size) {
    if (lines[index].trim() == marker) return index
  }
  return -1
}

private fun splitLines(text: String): List<String> {
  if (text.isEmpty()) return emptyList()

  val lines = ArrayList<String>()
  var lineStart = 0
  var index = 0
  while (index < text.length) {
    when (text[index]) {
      '\n' -> {
        lines += text.substring(lineStart, index)
        lineStart = index + 1
      }

      '\r' -> {
        lines += text.substring(lineStart, index)
        if (index + 1 < text.length && text[index + 1] == '\n') {
          index += 1
        }
        lineStart = index + 1
      }
    }
    index += 1
  }
  if (lineStart < text.length) {
    lines += text.substring(lineStart)
  }
  return lines
}

private fun ensureSafePatchPath(rawPath: String, label: String) {
  if (CONTROL_CHAR_REGEX.containsMatchIn(rawPath) || ESCAPED_CONTROL_REGEX.containsMatchIn(rawPath)) {
    mcpFail("$label path contains control characters or escape sequences")
  }
}
