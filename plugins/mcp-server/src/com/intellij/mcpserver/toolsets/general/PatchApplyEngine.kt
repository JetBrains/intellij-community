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
private const val DIFF_GIT_PREFIX = "diff --git "
private const val NO_NEWLINE_MARKER = "\\ No newline at end of file"

private val HEREDOC_PREFIXES = setOf("<<EOF", "<<'EOF'", "<<\"EOF\"")
private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val ESCAPED_CONTROL_REGEX = Regex("\\\\[nrt]")
private val UNIFIED_DIFF_HEADER_REGEX = Regex("^@@+\\s*-\\d+(?:,\\d+)?\\s+\\+\\d+(?:,\\d+)?\\s*@@+$")

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
    if (startIndex >= 0) {
      val endIndex = findMarkerIndex(lines, END_MARKER, startIndex + 1)
      if (endIndex < 0) mcpFail("patch must include *** End Patch")

      val bodyStart = startIndex + 1
      if (looksLikeGitDiff(lines, bodyStart, endIndex)) {
        return GitDiffParser(lines, bodyStart, endIndex).parseOperations()
      }
      return PatchParser(lines, bodyStart, endIndex).parseOperations()
    }

    if (looksLikeGitDiff(lines, 0, lines.size)) {
      return GitDiffParser(lines, 0, lines.size).parseOperations()
    }

    mcpFail("patch must include *** Begin Patch")
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

private class PatchParser(
  private val lines: List<String>,
  startIndex: Int,
  private val endIndex: Int,
) {
  private var index: Int = startIndex

  fun parseOperations(): List<PatchOperation> {
    val operations = mutableListOf<PatchOperation>()

    while (index < endIndex) {
      val line = lines[index]
      val headerLine = line.trimStart()

      when {
        headerLine.startsWith(ADD_PREFIX) -> operations += parseAdd(headerLine)
        headerLine.startsWith(DELETE_PREFIX) -> operations += parseDelete(headerLine)
        headerLine.startsWith(UPDATE_PREFIX) -> operations += parseUpdate(headerLine)
        line.trim().isEmpty() -> index += 1
        else -> mcpFail("Unexpected patch line: $line")
      }
    }

    if (operations.isEmpty()) mcpFail("patch did not contain any operations")
    return operations
  }

  private fun parseAdd(headerLine: String): AddPatchOperation {
    val path = headerLine.removePrefix(ADD_PREFIX).trim()
    if (path.isEmpty()) mcpFail("Add File requires a path")
    ensureSafePatchPath(path, "Add File")
    index += 1

    val content = StringBuilder()
    var hasContent = false
    while (index < endIndex && !isPatchHeaderLine(lines[index])) {
      val contentLine = lines[index]
      if (!contentLine.startsWith('+')) mcpFail("Add File lines must start with +")
      content.append(contentLine, 1, contentLine.length)
      content.append('\n')
      hasContent = true
      index += 1
    }

    return AddPatchOperation(path = path, content = if (hasContent) content.toString() else "")
  }

  private fun parseDelete(headerLine: String): DeletePatchOperation {
    val path = headerLine.removePrefix(DELETE_PREFIX).trim()
    if (path.isEmpty()) mcpFail("Delete File requires a path")
    ensureSafePatchPath(path, "Delete File")
    index += 1
    return DeletePatchOperation(path)
  }

  private fun parseUpdate(headerLine: String): UpdatePatchOperation {
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

      hunks += parseHunk(isFirstHunk = hunks.isEmpty())
    }

    if (hunks.isEmpty()) mcpFail("Update File requires at least one hunk")
    return UpdatePatchOperation(path = path, moveTo = moveTo, hunks = hunks)
  }

  private fun parseHunk(isFirstHunk: Boolean): PatchHunk {
    var header: String? = null
    var allowsStrictPair = false
    val line = lines[index]
    if (isHunkHeaderLine(line)) {
      val trimmedHeader = line.trim()
      val headerText = stripUnifiedDiffHeader(trimmedHeader)
      header = headerText.ifEmpty { null }
      allowsStrictPair = trimmedHeader == "@@"
      index += 1
    }
    else if (isFirstHunk) {
      if (!isDiffLine(line)) mcpFail("Expected @@ hunk header")
    }
    else {
      mcpFail("Expected @@ hunk header")
    }

    if (allowsStrictPair && index < endIndex && isStrictPairBlockStart(lines[index])) {
      return parseStrictPairHunk()
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
    return PatchHunk(header = header, lines = hunkLines, isEndOfFile = isEndOfFile)
  }

  private fun parseStrictPairHunk(): PatchHunk {
    val oldLines = mutableListOf<String>()
    var hasSecondDelimiter = false

    while (index < endIndex && !isPatchHeaderLine(lines[index])) {
      val line = lines[index]
      if (line.trim() == "@@") {
        hasSecondDelimiter = true
        index += 1
        break
      }
      oldLines += line
      index += 1
    }

    if (!hasSecondDelimiter) {
      mcpFail("Strict @@ pair hunk requires second @@ delimiter")
    }

    val newLines = mutableListOf<String>()
    while (index < endIndex && !isPatchHeaderLine(lines[index]) && !isHunkHeaderLine(lines[index])) {
      val line = lines[index]
      newLines += line
      index += 1
    }

    if (oldLines.isEmpty() && newLines.isEmpty()) mcpFail("Empty hunk in Update File")

    val hunkLines = ArrayList<PatchHunkLine>(oldLines.size + newLines.size)
    for (line in oldLines) {
      hunkLines += PatchHunkLine(prefix = '-', text = line)
    }
    for (line in newLines) {
      hunkLines += PatchHunkLine(prefix = '+', text = line)
    }
    return PatchHunk(header = null, lines = hunkLines, isEndOfFile = false)
  }

  private fun isStrictPairBlockStart(line: String): Boolean {
    if (isPatchHeaderLine(line) || isHunkHeaderLine(line)) return false
    return !isPrefixedDiffLine(line)
  }

  private fun isPrefixedDiffLine(line: String): Boolean {
    return line.isNotEmpty() && isDiffPrefix(line.first())
  }
}

private class GitDiffParser(
  private val lines: List<String>,
  startIndex: Int,
  private val endIndex: Int,
) {
  private var index: Int = startIndex

  fun parseOperations(): List<PatchOperation> {
    val operations = mutableListOf<PatchOperation>()
    while (index < endIndex) {
      while (index < endIndex && lines[index].trim().isEmpty()) {
        index += 1
      }
      if (index >= endIndex) {
        break
      }
      operations += parseOperation()
    }

    if (operations.isEmpty()) mcpFail("patch did not contain any operations")
    return operations
  }

  private fun parseOperation(): PatchOperation {
    var oldPath: String? = null
    var newPath: String? = null
    var renameFrom: String? = null
    var renameTo: String? = null
    val hunks = mutableListOf<PatchHunk>()
    var sawGitSignal = false

    while (index < endIndex) {
      val line = lines[index]
      val trimmed = line.trimStart()

      if (trimmed.isEmpty()) {
        index += 1
        continue
      }

      if (trimmed.startsWith(DIFF_GIT_PREFIX)) {
        if (sawGitSignal) {
          break
        }
        sawGitSignal = true
        parseDiffGitHeaderPaths(trimmed)?.let { (fromPath, toPath) ->
          oldPath = fromPath
          newPath = toPath
        }
        index += 1
        continue
      }

      if (line.startsWith("--- ")) {
        oldPath = parseGitMarkerPath(line.removePrefix("--- "))
        sawGitSignal = true
        index += 1
        continue
      }

      if (line.startsWith("+++ ")) {
        newPath = parseGitMarkerPath(line.removePrefix("+++ "))
        sawGitSignal = true
        index += 1
        continue
      }

      if (trimmed.startsWith("rename from ")) {
        renameFrom = parseGitRenamePath(trimmed.removePrefix("rename from "))
        sawGitSignal = true
        index += 1
        continue
      }

      if (trimmed.startsWith("rename to ")) {
        renameTo = parseGitRenamePath(trimmed.removePrefix("rename to "))
        sawGitSignal = true
        index += 1
        continue
      }

      if (trimmed == NO_NEWLINE_MARKER) {
        index += 1
        continue
      }

      if (trimmed.startsWith("Binary files ") || trimmed == "GIT binary patch") {
        mcpFail("Binary git patch is not supported")
      }

      if (isGitMetadataLine(trimmed)) {
        sawGitSignal = true
        index += 1
        continue
      }

      if (isHunkHeaderLine(line)) {
        sawGitSignal = true
        hunks += parseUnifiedHunk()
        continue
      }

      if (!sawGitSignal) {
        mcpFail("Unexpected patch line: $line")
      }
      break
    }

    if (!sawGitSignal) {
      mcpFail("patch did not contain any operations")
    }

    val sourcePath = renameFrom ?: oldPath
    val targetPath = renameTo ?: newPath
    return buildOperation(sourcePath, targetPath, hunks)
  }

  private fun parseUnifiedHunk(): PatchHunk {
    val headerText = stripUnifiedDiffHeader(lines[index].trim())
    val header = headerText.ifEmpty { null }
    index += 1

    val hunkLines = mutableListOf<PatchHunkLine>()
    var isEndOfFile = false
    while (index < endIndex) {
      val line = lines[index]
      val trimmed = line.trimStart()
      if (trimmed.startsWith(DIFF_GIT_PREFIX) || line.startsWith("--- ") || line.startsWith("+++ ") || isHunkHeaderLine(line)) {
        break
      }
      if (trimmed == NO_NEWLINE_MARKER) {
        index += 1
        continue
      }
      if (line == END_OF_FILE) {
        isEndOfFile = true
        index += 1
        break
      }
      if (line.isEmpty()) {
        hunkLines += PatchHunkLine(prefix = ' ', text = "")
        index += 1
        continue
      }
      val prefix = line.first()
      if (!isDiffPrefix(prefix)) {
        if (hunkLines.isEmpty()) {
          mcpFail("Hunk lines must start with space, +, or -")
        }
        break
      }

      hunkLines += PatchHunkLine(prefix = prefix, text = line.substring(1))
      index += 1
    }

    if (hunkLines.isEmpty()) mcpFail("Empty hunk in Update File")
    return PatchHunk(header = header, lines = hunkLines, isEndOfFile = isEndOfFile)
  }

  private fun buildOperation(sourcePath: String?, targetPath: String?, hunks: List<PatchHunk>): PatchOperation {
    if (sourcePath == null && targetPath == null) {
      mcpFail("Could not determine file path from git diff")
    }

    if (sourcePath == null) {
      val path = targetPath ?: mcpFail("Could not determine file path from git diff")
      ensureSafePatchPath(path, "Add File")
      val content = if (hunks.isEmpty()) "" else PatchApplyEngine.applyHunks("", hunks)
      return AddPatchOperation(path = path, content = content)
    }

    if (targetPath == null) {
      ensureSafePatchPath(sourcePath, "Delete File")
      return DeletePatchOperation(path = sourcePath)
    }

    ensureSafePatchPath(sourcePath, "Update File")
    ensureSafePatchPath(targetPath, "Move to")
    return UpdatePatchOperation(
      path = sourcePath,
      moveTo = if (sourcePath == targetPath) null else targetPath,
      hunks = hunks,
    )
  }
}

private fun buildHunkLines(lines: List<PatchHunkLine>): Pair<List<String>, List<String>> {
  var oldSize = 0
  var newSize = 0
  for (line in lines) {
    when (line.prefix) {
      ' ' -> {
        oldSize += 1
        newSize += 1
      }

      '-' -> oldSize += 1
      '+' -> newSize += 1
    }
  }

  val oldLines = ArrayList<String>(oldSize)
  val newLines = ArrayList<String>(newSize)

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

  return SequenceFinder(haystack, needle).find(searchStart, maxStart)
}

private enum class MatchMode {
  TRIM_END,
  TRIM,
  NORMALIZED,
}

private class SequenceFinder(
  private val haystack: List<String>,
  private val needle: List<String>,
) {
  private var trimEndNeedle: Array<String>? = null
  private var trimNeedle: Array<String>? = null
  private var normalizedNeedle: Array<String>? = null

  private var trimEndHaystack: TransformedLineCache? = null
  private var trimHaystack: TransformedLineCache? = null
  private var normalizedHaystack: TransformedLineCache? = null

  fun find(searchStart: Int, maxStart: Int): Int {
    searchExact(searchStart, maxStart).takeIf { it >= 0 }?.let { return it }
    searchWithMode(MatchMode.TRIM_END, searchStart, maxStart).takeIf { it >= 0 }?.let { return it }
    searchWithMode(MatchMode.TRIM, searchStart, maxStart).takeIf { it >= 0 }?.let { return it }
    return searchWithMode(MatchMode.NORMALIZED, searchStart, maxStart)
  }

  private fun searchExact(searchStart: Int, maxStart: Int): Int {
    return searchKmp(
      searchStart = searchStart,
      searchEndInclusive = maxStart + needle.size - 1,
      needleSize = needle.size,
      haystackLine = { index -> haystack[index] },
      needleLine = { index -> needle[index] },
    )
  }

  private fun searchWithMode(mode: MatchMode, searchStart: Int, maxStart: Int): Int {
    val transformedNeedle = transformedNeedle(mode)
    val transformedHaystack = transformedHaystack(mode)
    return searchKmp(
      searchStart = searchStart,
      searchEndInclusive = maxStart + transformedNeedle.size - 1,
      needleSize = transformedNeedle.size,
      haystackLine = transformedHaystack::line,
      needleLine = transformedNeedle::get,
    )
  }

  private fun transformedNeedle(mode: MatchMode): Array<String> {
    return when (mode) {
      MatchMode.TRIM_END -> trimEndNeedle ?: transformNeedle(mode).also { trimEndNeedle = it }
      MatchMode.TRIM -> trimNeedle ?: transformNeedle(mode).also { trimNeedle = it }
      MatchMode.NORMALIZED -> normalizedNeedle ?: transformNeedle(mode).also { normalizedNeedle = it }
    }
  }

  private fun transformNeedle(mode: MatchMode): Array<String> {
    val transformed = Array(needle.size) { "" }
    for (index in needle.indices) {
      transformed[index] = transformLine(needle[index], mode)
    }
    return transformed
  }

  private fun transformedHaystack(mode: MatchMode): TransformedLineCache {
    return when (mode) {
      MatchMode.TRIM_END -> trimEndHaystack ?: TransformedLineCache(haystack, mode).also { trimEndHaystack = it }
      MatchMode.TRIM -> trimHaystack ?: TransformedLineCache(haystack, mode).also { trimHaystack = it }
      MatchMode.NORMALIZED -> normalizedHaystack ?: TransformedLineCache(haystack, mode).also { normalizedHaystack = it }
    }
  }
}

private class TransformedLineCache(
  private val source: List<String>,
  private val mode: MatchMode,
) {
  private val cache = arrayOfNulls<String>(source.size)

  fun line(index: Int): String {
    val cached = cache[index]
    if (cached != null) return cached

    val transformed = transformLine(source[index], mode)
    cache[index] = transformed
    return transformed
  }
}

private inline fun searchKmp(
  searchStart: Int,
  searchEndInclusive: Int,
  needleSize: Int,
  haystackLine: (Int) -> String,
  needleLine: (Int) -> String,
): Int {
  if (needleSize == 1) {
    val expected = needleLine(0)
    for (haystackIndex in searchStart..searchEndInclusive) {
      if (haystackLine(haystackIndex) == expected) return haystackIndex
    }
    return -1
  }

  val prefix = buildPrefixTable(needleSize, needleLine)
  var matched = 0
  for (haystackIndex in searchStart..searchEndInclusive) {
    val value = haystackLine(haystackIndex)
    while (matched > 0 && value != needleLine(matched)) {
      matched = prefix[matched - 1]
    }
    if (value == needleLine(matched)) {
      matched += 1
      if (matched == needleSize) {
        return haystackIndex - needleSize + 1
      }
    }
  }
  return -1
}

private inline fun buildPrefixTable(
  needleSize: Int,
  needleLine: (Int) -> String,
): IntArray {
  val prefix = IntArray(needleSize)
  var matched = 0
  for (index in 1 until needleSize) {
    val value = needleLine(index)
    while (matched > 0 && value != needleLine(matched)) {
      matched = prefix[matched - 1]
    }
    if (value == needleLine(matched)) {
      matched += 1
      prefix[index] = matched
    }
  }
  return prefix
}

private fun transformLine(text: String, mode: MatchMode): String {
  return when (mode) {
    MatchMode.TRIM_END -> text.trimEnd()
    MatchMode.TRIM -> text.trim()
    MatchMode.NORMALIZED -> normalizeForMatch(text)
  }
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

private fun stripUnifiedDiffHeader(trimmed: String): String {
  if (UNIFIED_DIFF_HEADER_REGEX.matches(trimmed)) return ""
  return if (trimmed.length > 2) trimmed.substring(2).trim() else ""
}

private fun findMarkerIndex(lines: List<String>, marker: String, start: Int = 0): Int {
  for (index in start until lines.size) {
    if (lines[index].trim() == marker) return index
  }
  return -1
}

private fun looksLikeGitDiff(lines: List<String>, start: Int, end: Int): Boolean {
  var hasFileMarkers = false
  for (index in start until end) {
    val line = lines[index]
    val trimmed = line.trimStart()
    if (trimmed.startsWith(DIFF_GIT_PREFIX)) return true
    if (line.startsWith("--- ") || line.startsWith("+++ ")) {
      hasFileMarkers = true
      continue
    }
    if (trimmed.startsWith("rename from ") || trimmed.startsWith("rename to ")) return true
  }
  return hasFileMarkers
}

private fun parseDiffGitHeaderPaths(line: String): Pair<String, String>? {
  val payload = line.removePrefix(DIFF_GIT_PREFIX).trim()
  if (payload.isEmpty()) return null
  val tokens = payload.split(' ', limit = 3)
  if (tokens.size < 2) return null
  val fromPath = normalizeGitMarkerPath(tokens[0]) ?: return null
  val toPath = normalizeGitMarkerPath(tokens[1]) ?: return null
  return fromPath to toPath
}

private fun parseGitMarkerPath(rawValue: String): String? {
  val marker = rawValue.substringBefore('\t').trim()
  return normalizeGitMarkerPath(marker)
}

private fun parseGitRenamePath(rawValue: String): String {
  val path = unquoteGitPath(rawValue.trim())
  if (path.isEmpty()) {
    mcpFail("Could not determine file path from git diff")
  }
  return path
}

private fun normalizeGitMarkerPath(rawValue: String): String? {
  val value = unquoteGitPath(rawValue)
  if (value == "/dev/null") return null
  return when {
    value.startsWith("a/") || value.startsWith("b/") -> value.substring(2)
    else -> value
  }
}

private fun unquoteGitPath(rawValue: String): String {
  val value = rawValue.trim()
  if (value.length < 2 || value.first() != '"' || value.last() != '"') return value
  val inner = value.substring(1, value.length - 1)
  return inner
    .replace("\\\\", "\\")
    .replace("\\\"", "\"")
}

private fun isGitMetadataLine(trimmed: String): Boolean {
  return trimmed.startsWith("index ") ||
         trimmed.startsWith("old mode ") ||
         trimmed.startsWith("new mode ") ||
         trimmed.startsWith("new file mode ") ||
         trimmed.startsWith("deleted file mode ") ||
         trimmed.startsWith("similarity index ") ||
         trimmed.startsWith("dissimilarity index ")
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
