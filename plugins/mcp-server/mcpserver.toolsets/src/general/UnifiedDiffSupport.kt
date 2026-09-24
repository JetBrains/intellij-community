// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.general

import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.ApiStatus

/** How many unchanged lines to keep around each change, as in a standard unified diff. */
private const val CONTEXT_LINES: Int = 3

/** The maximum number of diff lines to report, so a large change cannot flood the agent context. */
private const val MAX_DIFF_LINES: Int = 200

/** The smallest useful hunk: the `@@` header line, and one line under it. */
private const val MIN_HUNK_LINES: Int = 2

/**
 * Marks the last line of a text that has no final line separator. The mark makes such a line different
 * from the same line with a separator, so the diff also reports a change of the final separator alone.
 * The mark never reaches the output. [addDiffLine] replaces it with [NO_FINAL_SEPARATOR_LINE].
 */
private const val NO_FINAL_SEPARATOR_MARK: String = "\u0000<no final line separator>"

/** The standard unified-diff line that reports a missing final line separator. */
private const val NO_FINAL_SEPARATOR_LINE: String = "\\ No newline at end of file"

/**
 * One file that a tool processed: the requested path, and the text before and after the change.
 *
 * [before] and [after] are `null` when the text of the file is not available, for example a binary
 * file. The diff then reports that. The caller never gets a silent "the file did not change".
 */
@ApiStatus.Internal
class ChangedFileText(val path: String, val before: String?, val after: String?)

/**
 * Returns a unified diff of the changes in [files]. Returns `null` when nothing changed.
 *
 * The result holds at most [maxLines] diff lines. A file goes in whole, or the footer names it. A hunk
 * that must be cut keeps a header that counts the lines the hunk really prints.
 *
 * Each caller decides where the two texts come from. A caller that takes both from the IDE document
 * gets a diff of its own change only, because the `before` text already holds every earlier edit
 * (RIDER-141305).
 */
@ApiStatus.Internal
fun buildUnifiedDiff(files: List<ChangedFileText>, maxLines: Int = MAX_DIFF_LINES): String? {
  val lines = ArrayList<String>()
  val omittedFiles = ArrayList<String>()
  var omittedLines = 0

  for (file in files) {
    val fileDiff = fileDiff(file) ?: continue
    // Print a file whole, or leave it out and name it. A header with no hunk under it helps nobody.
    if (lines.isNotEmpty() && maxLines - lines.size < fileDiff.header.size + MIN_HUNK_LINES) {
      omittedFiles += fileDiff.path
      omittedLines += fileDiff.lineCount
      continue
    }
    lines += fileDiff.header
    for ((index, hunk) in fileDiff.hunks.withIndex()) {
      val room = maxLines - lines.size - 1
      if (room < 1 && index > 0) {
        omittedLines += fileDiff.hunks.subList(index, fileDiff.hunks.size).sumOf { it.lineCount }
        break
      }
      // The first hunk always keeps one line, so the header above it never stands alone.
      val bodyLines = maxOf(1, room)
      lines += hunk.render(bodyLines)
      omittedLines += maxOf(0, hunk.body.size - bodyLines)
    }
  }
  if (lines.isEmpty()) return null

  return buildString {
    lines.joinTo(this, "\n")
    if (omittedLines > 0) {
      append("\n… and $omittedLines more diff line(s) omitted.")
    }
    if (omittedFiles.isNotEmpty()) {
      append("\n… The diff leaves out these file(s): ${omittedFiles.joinToString(", ")}.")
    }
  }
}

/** The diff of one file: the `---` and `+++` header, and the hunks under it. */
private class FileDiff(val path: String, val header: List<String>, val hunks: List<DiffHunk>) {
  val lineCount: Int get() = header.size + hunks.sumOf { it.lineCount }
}

/** One hunk: where it starts on each side, and the `-`, `+`, and context lines under the `@@` header. */
private class DiffHunk(private val start0: Int, private val start1: Int, val body: List<String>) {
  val lineCount: Int get() = body.size + 1

  /** Renders the hunk with at most [maxBodyLines] body lines. The header counts the lines it prints. */
  fun render(maxBodyLines: Int): List<String> {
    val printed = if (body.size <= maxBodyLines) body else body.subList(0, maxBodyLines)
    val count0 = printed.count { it.startsWith(" ") || it.startsWith("-") }
    val count1 = printed.count { it.startsWith(" ") || it.startsWith("+") }
    return listOf("@@ -${hunkStart(start0, count0)},$count0 +${hunkStart(start1, count1)},$count1 @@") + printed
  }
}

private fun fileDiff(file: ChangedFileText): FileDiff? {
  val path = file.path.diffPath()
  val header = listOf("--- a/$path", "+++ b/$path")
  if (file.before == null || file.after == null) {
    return note(path, header, "the content of this file is not available, so the diff cannot report what changed; read the file again")
  }
  if (file.before == file.after) return null

  val beforeLines = splitDiffLines(file.before)
  val afterLines = splitDiffLines(file.after)
  val firstChange = try {
    Diff.buildChanges(beforeLines, afterLines)
  }
  catch (_: FilesTooBigForDiffException) {
    return note(path, header, "this file changed, but it is too large to diff")
  }
  // The two texts differ, but the lines are equal. Only the line separator style changed.
  if (firstChange == null) {
    return note(path, header, "only the line separators of this file changed")
  }

  val hunks = splitIntoHunks(firstChange.toList()).map { hunk -> buildHunk(hunk, beforeLines, afterLines) }
  return FileDiff(path, header, hunks)
}

/** Reports a file that the diff cannot show line by line. The file gets a header, and no hunk. */
private fun note(path: String, header: List<String>, text: String): FileDiff = FileDiff(path, header + "($text)", emptyList())

/**
 * Splits [text] into the lines that the diff compares. A text that ends with a line separator gets no
 * extra empty line at the end, which is what [Diff.splitLines] alone adds. A text with no final
 * separator marks its last line, so a change of the final separator alone still counts as a change.
 */
private fun splitDiffLines(text: String): Array<String> {
  if (text.isEmpty()) return emptyArray()
  val lines = Diff.splitLines(text)
  val lastIndex = lines.size - 1
  if (text.endsWith('\n') || text.endsWith('\r')) return lines.copyOfRange(0, lastIndex)
  lines[lastIndex] += NO_FINAL_SEPARATOR_MARK
  return lines
}

/**
 * Puts the changes into hunks. Two changes share a hunk when their context lines touch, exactly as a
 * unified diff joins them.
 */
private fun splitIntoHunks(changes: List<Diff.Change>): List<List<Diff.Change>> {
  val hunks = ArrayList<MutableList<Diff.Change>>()
  for (change in changes) {
    val currentHunk = hunks.lastOrNull()
    val previous = currentHunk?.last()
    if (previous != null && change.line0 - (previous.line0 + previous.deleted) <= CONTEXT_LINES * 2) {
      currentHunk += change
    }
    else {
      hunks += mutableListOf(change)
    }
  }
  return hunks
}

private fun buildHunk(hunk: List<Diff.Change>, beforeLines: Array<String>, afterLines: Array<String>): DiffHunk {
  val first = hunk.first()
  val last = hunk.last()

  // Clamp the context on both sides together. A different clamp per side would shift the two line
  // numbers in the hunk header away from each other.
  val leadingContext = minOf(CONTEXT_LINES, first.line0, first.line1)
  val start0 = first.line0 - leadingContext
  val start1 = first.line1 - leadingContext
  val changeEnd0 = last.line0 + last.deleted
  val changeEnd1 = last.line1 + last.inserted
  val trailingContext = minOf(CONTEXT_LINES, beforeLines.size - changeEnd0, afterLines.size - changeEnd1)
  val end0 = changeEnd0 + trailingContext

  val body = ArrayList<String>()
  var line0 = start0
  for (change in hunk) {
    while (line0 < change.line0) body.addDiffLine(" ", beforeLines[line0++])
    for (index in change.line0 until change.line0 + change.deleted) body.addDiffLine("-", beforeLines[index])
    for (index in change.line1 until change.line1 + change.inserted) body.addDiffLine("+", afterLines[index])
    line0 = change.line0 + change.deleted
  }
  while (line0 < end0) body.addDiffLine(" ", beforeLines[line0++])
  return DiffHunk(start0, start1, body)
}

/** Adds one diff line. A marked last line also gets the standard `\ No newline at end of file` line. */
private fun MutableList<String>.addDiffLine(prefix: String, line: String) {
  if (line.endsWith(NO_FINAL_SEPARATOR_MARK)) {
    this += prefix + line.removeSuffix(NO_FINAL_SEPARATOR_MARK)
    this += NO_FINAL_SEPARATOR_LINE
  }
  else {
    this += prefix + line
  }
}

/**
 * Converts a zero-based line index into the number that a unified diff header uses. An empty range
 * reports the line before the range, so it keeps the zero-based value.
 */
private fun hunkStart(start: Int, count: Int): Int = if (count == 0) start else start + 1

private fun String.diffPath(): String = replace('\\', '/')
