package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeFenceProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Extends default [CodeFenceProvider] to support additional MS Azure code fence syntax
 * for Mermaid.js fences.
 *
 * ```markdown
 * ::: mermaid
 * <mermaid diagram syntax>
 * :::
 * ```
 */
@ApiStatus.Experimental
open class CodeFenceMarkerProvider: CodeFenceProvider() {
  override fun obtainFenceOpeningInfo(pos: LookaheadText.Position, constraints: MarkdownConstraints): OpeningInfo? {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) {
      return null
    }
    val matchResult = openingRegex.find(pos.currentLineFromPosition) ?: return null
    val delimiter = matchResult.groups[1]?.value
    checkNotNull(delimiter) { "Failed to obtain delimiter group value from match result" }
    val info = matchResult.groups[2]?.value
    checkNotNull(info) { "Failed to obtain info string group value form match result" }
    if (delimiter.startsWith(":::") && info.trim().lowercase() != "mermaid") {
      return null
    }
    return OpeningInfo(delimiter, info)
  }

  companion object {
    private val openingRegex = Regex("^ {0,3}(~~~+|```+|:::+)([^`]*)\$")
  }
}
