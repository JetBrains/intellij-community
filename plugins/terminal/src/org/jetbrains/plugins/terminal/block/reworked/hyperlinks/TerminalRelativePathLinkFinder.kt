// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isWindows
import it.unimi.dsi.fastutil.chars.CharOpenHashSet
import it.unimi.dsi.fastutil.chars.CharSet
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalGenericFileFilter.Companion.FILENAME_MAX
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalGenericFileFilter.Companion.PATH_MIN

internal class TerminalRelativePathLinkFinder(
  private val project: Project,
  private val line: String,
  private val indexOffset: Int,
  eelDescriptor: EelDescriptor,
  private val initialWorkingDirectory: VirtualFile,
  private val foundLinkSink: (Filter.ResultItem) -> Unit
) {

  private val isWindows: Boolean = eelDescriptor.osFamily.isWindows
  private var dir: VirtualFile = initialWorkingDirectory
  private var pathStartIndex: Int = -1
  private var lastPathSegmentStart: Int = -1

  private fun isLinkSeparatedFromFollowingText(linkEndExclusiveIndex: Int): Boolean {
    return linkEndExclusiveIndex == line.length || isNonPathChar(line[linkEndExclusiveIndex])
  }

  private fun addLink(pathEndExclusiveIndex: Int, position: Position?, checkLinkSeparatedFromFollowingText: Boolean): Boolean {
    if (pathEndExclusiveIndex - pathStartIndex < PATH_MIN) {
      return false
    }
    val linkEndExclusiveIndex = position?.linkEndExclusiveIndex ?: pathEndExclusiveIndex
    if (checkLinkSeparatedFromFollowingText && !isLinkSeparatedFromFollowingText(linkEndExclusiveIndex)) {
      return false
    }
    val name = line.substring(lastPathSegmentStart, pathEndExclusiveIndex)
    if (pathStartIndex == lastPathSegmentStart && (name == ".." || name == ".")) {
      // avoid noisy links for ".." or "."
      return false
    }
    val child = if (name.isNotEmpty()) {
      findValidChild(dir, name) ?: return false
    }
    else {
      dir // directory path with a trailing slash
    }
    val oneBasedLine = position?.oneBasedLine ?: 1
    val oneBasedColumn = position?.oneBasedColumn ?: 1
    foundLinkSink(Filter.ResultItem(
      indexOffset + pathStartIndex,
      indexOffset + linkEndExclusiveIndex,
      TerminalOpenFileHyperlinkInfo(project, child, oneBasedLine - 1, oneBasedColumn - 1),
      EMPTY_ATTRS,
      null,
      HOVERED_ATTRS,
    ))
    return true
  }

  private fun addLinkWithoutTrailingDot(endInd: Int) {
    val pathEndExclusiveIndex = if (endInd > 0 && line[endInd - 1] == '.') endInd - 1 else endInd
    addLink(pathEndExclusiveIndex, null, false)
  }

  fun find() {
    var state = ParsingState.NORMAL
    var ind = 0
    while (ind < line.length) {
      when (state) {
        ParsingState.NORMAL -> {
          if (canStartRelativePath(ind)) {
            dir = initialWorkingDirectory
            state = ParsingState.PATH
            pathStartIndex = ind
            lastPathSegmentStart = ind
          }
        }
        ParsingState.PATH -> {
          if (ind - lastPathSegmentStart > FILENAME_MAX) {
            state = ParsingState.CANCELED_PATH
          }
          else {
            val char = line[ind]
            when {
              isSeparator(char) -> {
                val name = line.substring(lastPathSegmentStart, ind)
                val child = if (name.isNotEmpty()) findValidChild(dir, name)?.takeIf { it.isDirectory } else null
                if (child != null) {
                  lastPathSegmentStart = ind + 1
                  dir = child
                }
                else {
                  state = ParsingState.CANCELED_PATH
                }
              }
              char == ':' -> {
                val position = parsePosition(ind)
                addLink(ind, position, true)
                state = ParsingState.CANCELED_PATH
              }
              isNonPathChar(char) -> {
                addLinkWithoutTrailingDot(ind)
                state = ParsingState.NORMAL
              }
            }
          }
        }
        ParsingState.CANCELED_PATH -> {
          if (isNonPathChar(line[ind])) {
            state = ParsingState.NORMAL
          }
        }
      }
      ind++
    }
    if (state == ParsingState.PATH) {
      addLinkWithoutTrailingDot(ind)
    }
  }

  private fun parsePosition(colonInd: Int): Position? {
    // Try to parse as "path: (line, column)"
    if (line.startsWith(" (", colonInd + 1)) {
      var i = colonInd + 3
      val lineNumberStr = line.takeNumberFromIndex(i)
      if (lineNumberStr != null) {
        i += lineNumberStr.length
        if (line.startsWith(", ", i)) {
          i += 2
          val columnNumberStr = line.takeNumberFromIndex(i)
          if (columnNumberStr != null) {
            i += columnNumberStr.length
            if (line.getOrElse(i, ' ') == ')') {
              return Position(lineNumberStr.safeToIntOrDefault(1), columnNumberStr.safeToIntOrDefault(1), i + 1)
            }
          }
        }
      }
      return null
    }
    // Try to parse as path:line[:column]
    var i = colonInd + 1
    val lineNumberStr = line.takeNumberFromIndex(i)
    if (lineNumberStr != null) {
      i += lineNumberStr.length
      if (line.startsWith(":", i)) {
        i++
        val columnNumberStr = line.takeNumberFromIndex(i)
        if (columnNumberStr != null) {
          i += columnNumberStr.length
          return Position(lineNumberStr.safeToIntOrDefault(1), columnNumberStr.safeToIntOrDefault(1), i)
        }
      }
      return Position(lineNumberStr.safeToIntOrDefault(1), 1, i)
    }
    return null
  }

  private fun canStartRelativePath(ind: Int): Boolean {
    val char = line[ind]
    if (char.isWhitespace() || NON_PATH_START_CHARS.contains(char)) {
      return false
    }
    if (isWindows && OSAgnosticPathUtil.isDriveLetter(char) && line.getOrElse(ind + 1, ' ') == ':') {
      return false
    }
    return ind == 0 || isNonPathChar(line[ind - 1])
  }

  private fun findValidChild(parentDirectory: VirtualFile, name: String) : VirtualFile? {
    if (name == ".") {
      return parentDirectory
    }
    if (name == "..") {
      return parentDirectory.parent
    }
    if (parentDirectory is NewVirtualFile) {
      return parentDirectory.findChildIfCached(name)?.takeIf { it.isValid }
    }
    return null
  }
}

private data class Position(val oneBasedLine: Int, val oneBasedColumn: Int, val linkEndExclusiveIndex: Int)

private fun isSeparator(char: Char) = char == '/' || char == '\\'

private fun CharSequence.getOrElse(index: Int, defaultValue: Char): Char = this.getOrElse(index) { defaultValue }

private fun String.takeNumberFromIndex(startIndex: Int): String? {
  for (i in startIndex until length) {
    if (i - startIndex >= 7) {
      // Skip numbers >= 1000_000 as impossible line/column.
      // Also, it will avoid throwing NumberFormatException.
      return null
    }
    if (!this[i].isDigit()) {
      return if (i == startIndex) null else substring(startIndex, i)
    }
  }
  return null
}

private fun isNonPathChar(char: Char): Boolean {
  return char.isWhitespace() || NON_PATH_CHARS.contains(char)
}

private val NON_PATH_CHARS: CharSet = CharOpenHashSet("()[]<>{}\"'`:;,=|^!?*".toCharArray())
private val NON_PATH_START_CHARS: CharSet = CharOpenHashSet(NON_PATH_CHARS).also {
  it.addAll("/\\~#$%&+".toCharArray().toTypedArray())
}
