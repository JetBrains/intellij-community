// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath

/**
 * Finds absolute file path links in terminal output.
 *
 * Detects both Linux-style absolute paths (starting with `/`) and Windows-style absolute paths
 * (starting with drive letter like `C:\` or `C:/`).
 *
 * This class uses a state machine to parse paths character by character for maximum performance.
 * 
 * Originally, copy-pasted from `com.android.tools.idea.gradle.project.build.output.GenericFileFilter`.
 */
internal class TerminalAbsolutePathLinkFinder(
  private val project: Project,
  private val line: String,
  private val indexOffset: Int,
  private val localFileSystem: LocalFileSystem,
  private val eelDescriptor: EelDescriptor?,
  private val foundLinkSink: (Filter.ResultItem) -> Unit,
) {

  private var state = ParsingState.NORMAL
  private var pathStartIndex = -1
  private var lastPathSegmentStart = -1
  private var candidateItem: Filter.ResultItem? = null
  private var i = 0

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
    if (pathEndIndex - lastPathSegmentStart > FILENAME_MAX) return null
    if (pathEndIndex - pathStartIndex < PATH_MIN) return null

    val path = line.substring(pathStartIndex, pathEndIndex)
    if (path.all { it == '/' || it == '\\' }) {
      // Ignore single slashes, as these are probably referring to something
      // other than the file system root (e.g. progress indicators like "[10 / 1,000]").
      return null
    }
    val file = findFileByPathIfCached(path)
    return if (file != null) {
      createInvisibleLink(
        indexOffset + pathStartIndex,
        indexOffset + i,
        TerminalOpenFileHyperlinkInfo(project, file, lineNumber, columnNumber),
      )
    }
    else {
      null
    }
  }

  private fun findValidResultWithNumbers(pathEndIndex: Int): Filter.ResultItem? {
    val lineNumber: Int
    var columnNumber = 1
    // Try to parse as "path: (line, column):"
    if (line.startsWith(" (", i + 1)) {
      i += 3
      lineNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
      if (line.startsWith(", ", i)) {
        i += 2
        columnNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
        if (line.startsWith("):", i)) {
          i += 2
        }
      }
    }
    else {
      // Try to parse as path:line:column:
      lineNumber = line.takeWhileFromIndex(i + 1) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
      columnNumber =
        if (line.getOrNull(++i) == ':')
          line.takeWhileFromIndex(++i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
        else
          1
    }
    return findValidResult(pathEndIndex, lineNumber - 1, columnNumber - 1)
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
              if (i - pathStartIndex > 1){
                val currentCandidate = findValidResult(i, 0, 0)
                if (currentCandidate == null) {
                  /* This is not a valid path it means that continuing as a path no longer will result in a valid file, but this could be
                     the the start of a new path. Need to move back up to 4 characters since the path could include a drive letter.
                   */
                  if ((i - 3) > pathStartIndex && line[i - 1] == ':' && line[i - 2] in 'A'..'Z' && line[i - 3].isWhitespace()) {
                    i -= 5
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
              val possibleCandidate = findValidResult(i, 0, 0)
              if (possibleCandidate != null) {
                candidateItem = possibleCandidate
              }
            }
          }
        }
        ParsingState.CANCELED_PATH -> if (line[i].isWhitespace()) startNormalMode()
      }
      i++
    }
    candidateItem?.let {
      foundLinkSink(it)
    }
  }

  private fun String.takeWhileFromIndex(index: Int, predicate: (Char) -> Boolean): String? {
    for (i in index until length) {
      if (!predicate(get(i))) {
        return if (i == index) null else substring(index, i)
      }
    }
    return null
  }

  private fun findFileByPathIfCached(path: @NativePath String): VirtualFile? {
    return try {
      doFindFileByPathIfCached(path)
    }
    catch (_: Throwable) {
      // We interpret any exception to mean the file is not found.
      null
    }
  }

  private fun doFindFileByPathIfCached(pathString: @NativePath String): VirtualFile? {
    if (pathString.isBlank()) return null

    // pathString is a path in the environment of EelDescriptor.
    // We need to get the absolute NIO path (with prefix like `\\wsl.localhost\` - fully identifies the environment)
    // Otherwise, LocalFileSystem might confuse it with a path in the local environment.
    // Do not perform the conversion if the descriptor is local - it is not required + better performance.
    val absolutePath = if (eelDescriptor != null && eelDescriptor != LocalEelDescriptor) {
      val eelPath = EelPath.parse(pathString, eelDescriptor)
      val nioPath = eelPath.asNioPath()
      nioPath.toString()
    }
    else pathString

    return localFileSystem.findFileByPathIfCached(absolutePath)
  }
}
