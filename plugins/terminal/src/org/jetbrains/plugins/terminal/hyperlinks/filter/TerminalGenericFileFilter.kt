// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.filter

import com.intellij.execution.filters.ConsoleFilterProviderEx
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

internal enum class ParsingState {
  NORMAL, PATH, CANCELED_PATH
}

/**
 * High-performance file path hyperlink detection for terminal output.
 *
 * This filter detects both absolute and relative file paths in terminal output
 * and converts them into clickable invisible hyperlinks.
 * It uses manual character-by-character parsing with state machines instead of
 * regular expressions for maximum performance.
 */
@ApiStatus.Internal
class TerminalGenericFileFilter(
  private val project: Project,
  private val context: TerminalHyperlinkFilterContext?,
  private val localFileSystem: LocalFileSystem,
) : Filter {

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val indexOffset = entireLength - line.length
    val items = mutableListOf<Filter.ResultItem>()
    TerminalAbsolutePathLinkFinder(project, line, indexOffset, localFileSystem, context?.eelDescriptor, items::add).find()
    context?.currentWorkingDirectory?.let { workingDir ->
      TerminalRelativePathLinkFinder(project, line, indexOffset, context.eelDescriptor, workingDir, items::add).find()
    }
    return if (items.isEmpty()) null else Filter.Result(items)
  }
}

/**
 * Maximum filename length considered during parsing (per path segment).
 *
 * Do not confuse with file path length, which may contain several file names separated by '/' or '\'.
 * Modern popular file systems do not allow file names longer than 255 characters.
 */
@ApiStatus.Internal
const val FILENAME_MAX: Int = 255

/**
 * Minimum path length to be considered as a valid file path.
 *
 * Paths shorter than this are ignored. For example, lonely slashes (`/`) are excluded
 * to avoid false positives from progress indicators like `[10 / 1,000]`.
 */
internal const val PATH_MIN: Int = 2

internal fun String?.safeToIntOrDefault(default: Int): Int = StringUtil.parseInt(this, default)

/**
 * Creates an invisible hyperlink for a file path.
 */
internal fun createInvisibleLink(
  highlightStartOffset: Int,
  highlightEndOffset: Int,
  hyperlinkInfo: HyperlinkInfo,
): Filter.ResultItem = Filter.ResultItem(
  highlightStartOffset,
  highlightEndOffset,
  hyperlinkInfo,
).also { it.isInvisibleLink = true }

internal class TerminalGenericFileFilterProvider : ConsoleFilterProviderEx {
  override fun getDefaultFilters(project: Project, scope: GlobalSearchScope): Array<out Filter> {
    if (scope is TerminalFilterScope && Registry.`is`("terminal.generic.hyperlinks", false)) {
      val filterContext = scope.filterContext.takeIf {
        Registry.`is`("terminal.generic.hyperlinks.for.relative.path", true)
      }
      return arrayOf(TerminalGenericFileFilter(project, filterContext, LocalFileSystem.getInstance()))
    }
    return emptyArray()
  }

  override fun getDefaultFilters(project: Project): Array<out Filter?> = emptyArray()
}

/**
 * Provides access to lineNumber and columnNumber in tests.
 */
@ApiStatus.Internal
class TerminalOpenFileHyperlinkInfo(project: Project, file: VirtualFile, val lineNumber: Int, val columnNumber: Int) :
  OpenFileHyperlinkInfo(project, file, lineNumber, columnNumber)
