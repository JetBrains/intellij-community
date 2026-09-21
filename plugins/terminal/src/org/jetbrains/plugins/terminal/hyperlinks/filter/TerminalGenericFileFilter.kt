// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks.filter

import com.intellij.execution.filters.ConsoleFilterProviderEx
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

internal enum class ParsingState {
  NORMAL, PATH, CANCELED_PATH
}

/**
 * Finds file paths in terminal output and turns them into invisible hyperlinks.
 *
 * Detects absolute paths of the environment of [eelDescriptor], home-relative paths
 * (`~/`), and paths relative to the working directory of the shell. The last two need
 * [context]. A path becomes a link only if [fileLookup] finds a file at it, so the
 * lookup decides where files are searched for: the VFS cache for every output line,
 * or the environment of the terminal for the hovered line only.
 *
 * Paths are parsed character by character with state machines, not regular expressions.
 */
@ApiStatus.Internal
class TerminalGenericFileFilter(
  private val project: Project,
  private val eelDescriptor: EelDescriptor,
  private val context: TerminalHyperlinkFilterContext?,
  private val fileLookup: TerminalFileLookup,
  private val relativePaths: Boolean = true,
) : Filter {

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val indexOffset = entireLength - line.length
    val items = mutableListOf<Filter.ResultItem>()
    TerminalAbsolutePathLinkFinder(project, line, indexOffset, eelDescriptor, context?.userHomeDirectory, fileLookup, items::add).find()
    if (relativePaths) {
      context?.currentWorkingDirectory?.let { workingDirectory ->
        TerminalRelativePathLinkFinder(project, line, indexOffset, eelDescriptor, workingDirectory, fileLookup, items::add).find()
      }
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

/**
 * Creates the file path filter for the terminal session of [scope], looking files up
 * with [fileLookup], or returns `null` if generic file hyperlinks are disabled.
 */
@ApiStatus.Internal
fun createTerminalGenericFileFilter(
  project: Project,
  scope: TerminalFilterScope,
  fileLookup: TerminalFileLookup,
): TerminalGenericFileFilter? {
  if (!Registry.`is`("terminal.generic.hyperlinks", false)) return null
  val context = scope.filterContext
  return TerminalGenericFileFilter(
    project = project,
    eelDescriptor = context?.eelDescriptor ?: LocalEelDescriptor,
    context = context,
    fileLookup = fileLookup,
    relativePaths = Registry.`is`("terminal.generic.hyperlinks.for.relative.path", true),
  )
}

internal class TerminalGenericFileFilterProvider : ConsoleFilterProviderEx {
  override fun getDefaultFilters(project: Project, scope: GlobalSearchScope): Array<out Filter> {
    // In the hover mode, TerminalHoverHyperlinkFilterProvider provides the same links for the hovered line only.
    if (scope !is TerminalFilterScope || Registry.`is`("terminal.hyperlinks.on.hover", true)) return emptyArray()
    // Every output line is filtered, so only the VFS cache is cheap enough to consult.
    val fileLookup = TerminalVfsFileLookup(LocalFileSystem.getInstance())
    val filter = createTerminalGenericFileFilter(project, scope, fileLookup) ?: return emptyArray()
    return arrayOf(filter)
  }

  override fun getDefaultFilters(project: Project): Array<out Filter?> = emptyArray()
}
