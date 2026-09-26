// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFileHyperlinkInfo

/**
 * Finds absolute file path links in terminal output.
 *
 * Detects Linux-style absolute paths (starting with `/`), Windows-style absolute paths
 * (starting with drive letter like `C:\` or `C:/`), and home-relative paths (starting with `~/` or `~\`).
 * A path becomes a link only if [fileLookup] finds a file at it.
 *
 * This class uses a state machine to parse paths character by character for maximum performance.
 *
 * Originally, copy-pasted from `com.android.tools.idea.gradle.project.build.output.GenericFileFilter`.
 */
internal class TerminalAbsolutePathLinkFinder(
  private val project: Project,
  private val line: String,
  private val indexOffset: Int,
  private val eelDescriptor: EelDescriptor,
  private val homeDirectory: EelPath?,
  private val fileLookup: TerminalFileLookup,
  private val foundLinkSink: (Filter.ResultItem) -> Unit,
) {

  private var state = ParsingState.NORMAL
  private var pathStartIndex = -1
  private var lastPathSegmentStart = -1
  private var candidateItem: Filter.ResultItem? = null
  private var i = 0
  private var hasSeenWhitespaceInPath = false

  private fun addPreviousCandidate() {
    candidateItem?.let {
      foundLinkSink(it)
    }
    candidateItem = null
  }

  private fun startPathMode(){
    state = ParsingState.PATH
    pathStartIndex = i
    lastPathSegmentStart = i
    hasSeenWhitespaceInPath = false
    addPreviousCandidate()
  }

  private fun startNormalMode() {
    state = ParsingState.NORMAL
    addPreviousCandidate()
  }

  private fun startCanceledMode() {
    state = ParsingState.CANCELED_PATH
    addPreviousCandidate()
  }

  private fun findValidResult(pathEndIndex: Int, lineNumber: Int, columnNumber: Int): Filter.ResultItem? {
    val path = findFile(pathEndIndex) ?: return null
    return createInvisibleLink(
      indexOffset + pathStartIndex,
      indexOffset + i,
      TerminalFileHyperlinkInfo(project, path, lineNumber, columnNumber),
    )
  }

  /**
   * Resolves `line[pathStartIndex, pathEndIndex)` as a file path.
   * Returns `null` if it is not a plausible path or there is no such file.
   */
  private fun findFile(pathEndIndex: Int): EelPath? {
    if (pathEndIndex - lastPathSegmentStart > FILENAME_MAX) return null
    if (pathEndIndex - pathStartIndex < PATH_MIN) return null

    val path = line.substring(pathStartIndex, pathEndIndex)
    if (path.all { it == '/' || it == '\\' }) {
      // Ignore single slashes, as these are probably referring to something
      // other than the file system root (e.g. progress indicators like "[10 / 1,000]").
      return null
    }
    val resolvedPath = if (path[0] == '~') {
      // '~' is only entered in PATH mode when immediately followed by a separator, see `find()`.
      homeDirectory?.let { it.toString() + path.substring(1) } ?: return null
    }
    else path
    return lookUp(resolvedPath)
  }

  /**
   * Checks the directory part of the current path (up to the last separator).
   * If it does not exist, no continuation of the path (e.g. with spaces in the last segment) can exist either.
   */
  private fun directoryPrefixMayExist(): Boolean {
    val prefixEnd = lastPathSegmentStart - 1 // index of the last separator
    if (prefixEnd - pathStartIndex <= 1) {
      return true // only a root or '~' before the last separator, e.g. "/foo", "~/foo", "C:\foo"
    }
    return findFile(prefixEnd) != null
  }

  private fun findValidResultWithNumbers(pathEndIndex: Int): Filter.ResultItem? {
    val position = parsePosition(line, i)
    if (position == null) {
      return findValidResult(pathEndIndex, 0, 0)
    }
    i = position.linkEndExclusiveIndex
    return findValidResult(pathEndIndex, position.oneBasedLine - 1, position.oneBasedColumn - 1)
  }

  fun find() {
    while (i < line.length) {
      when (state) {
        ParsingState.NORMAL -> {
          when {
            line[i] == '/' -> {
              // Start parsing a Linux path
              startPathMode()
            }
            line[i] == '~' && (line.getOrNull(i + 1) == '/' || line.getOrNull(i + 1) == '\\') -> {
              // Start parsing a home-relative path, e.g. "~/foo" or "~\foo"
              startPathMode()
            }
            line[i] in 'A'..'Z' && (line.startsWith(":\\", startIndex = i + 1) || line.startsWith(":/", startIndex = i + 1) ) -> {
              // Start parsing a Windows path
              startPathMode()
              i += 2
            }
          }
        }
        ParsingState.PATH -> {
          if ((i - lastPathSegmentStart) > FILENAME_MAX) {
            startCanceledMode()
          }
          else when {
            line[i] == '\\' || line[i] == '/' -> {
              lastPathSegmentStart = i + 1
              // A path without whitespace is looked up once, at the whitespace, ':' or end of line following it.
              // Only a path with whitespace is validated at each separator, see `directoryPrefixMayExist()`.
              if (hasSeenWhitespaceInPath && i - pathStartIndex > 1) {
                val currentCandidate = findValidResult(i, 0, 0)
                if (currentCandidate == null) {
                  // Continuing as a path can no longer result in a valid file, but this could be the start of a new path.
                  // (A Windows path cannot start here: ':' in PATH state is handled by the ':' branch below.)
                  if ((i - 2) > pathStartIndex && line[i - 1] == '~' && line[i - 2].isWhitespace()) {
                    // Could be the start of a new home-relative path, e.g. "... ~/foo"
                    i -= 3
                    startNormalMode()
                  }
                  else if ((i - 1) > pathStartIndex && line[i -1].isWhitespace()) {
                    i -= 2
                    startNormalMode()
                  }
                  else {
                    startCanceledMode()
                  }
                }
              }
            }
            line[i] == ':' -> {
              val previousI = i
              val longestCandidate = findValidResultWithNumbers(i)
              if (longestCandidate != null) {
                candidateItem = longestCandidate
              }
              else {
                // Could not parse numbers correctly (or is not a valid path, restore i)
                i = previousI
                // Could be the start of a windows path, move back in that case
                if (((i - 1) > pathStartIndex)
                    && (line[i - 1] in 'A'..'Z')
                    && ((i + 1) < line.length)
                    && (line[i + 1] == '/' || line[i + 1] == '\\')) {
                  i -= 2
                }
              }
              startNormalMode()
            }
            // Paths can have white spaces and links get cut early (https://issuetracker.google.com/issues/136242040)
            // Or can be a valid path but be the prefix of a longer path (for example "/work projects/" and "/work projects 2" exist,
            // https://issuetracker.google.com/issues/167701951)
            line[i].isWhitespace() -> {
              val isFirstWhitespaceInPath = !hasSeenWhitespaceInPath
              hasSeenWhitespaceInPath = true
              val possibleCandidate = findValidResult(i, 0, 0)
              if (possibleCandidate != null) {
                candidateItem = possibleCandidate
              }
              else if (isFirstWhitespaceInPath && !directoryPrefixMayExist()) {
                // Neither the path nor its directory exists, so no continuation of this path can exist either.
                startNormalMode()
              }
            }
          }
        }
        ParsingState.CANCELED_PATH -> if (line[i].isWhitespace()) startNormalMode()
      }
      i++
    }
    // Normally, the line ends with a line break, but let's support other cases too.
    // Check if the previous character is whitespace to avoid work duplication.
    if (state == ParsingState.PATH && !line[i - 1].isWhitespace()) {
      findValidResult(i, 0, 0)?.let { candidateItem = it }
    }
    candidateItem?.let {
      foundLinkSink(it)
    }
  }

  /** Returns [path] parsed for the terminal's environment if a file exists there. */
  private fun lookUp(path: String): EelPath? {
    if (path.isBlank()) return null
    val eelPath = try {
      EelPath.parse(path, eelDescriptor)
    }
    catch (_: EelPathException) {
      return null // not an absolute path in the environment of the terminal, e.g. a Windows path on Linux
    }
    return eelPath.takeIf { fileLookup.lookup(it) != null }
  }
}
