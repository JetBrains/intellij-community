// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.util

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownLinkFragmentUtil {
  private val lineFragmentPatterns = listOf(
    Regex("""L(\d+)(?:-L?(\d+))?"""),    // GitHub, GitLab
    Regex("""(?:lines-)?(\d+)(?:[:,-].*)?"""), // Bitbucket -> navigate to first line, ignore the rest
  )

  @JvmStatic
  fun getFragmentRange(elementText: String, valueTextRange: TextRange): TextRange? {
    val anchorOffset = elementText.indexOf('#')
    if (anchorOffset == -1) {
      return null
    }
    val endOffset = valueTextRange.endOffset
    val endIndex = if (endOffset <= anchorOffset) anchorOffset + 1 else endOffset
    return TextRange(anchorOffset + 1, endIndex)
  }

  @JvmStatic
  fun parseGitHubLineRange(fragment: String): IntRange? = lineFragmentPatterns.firstNotNullOfOrNull { parsePattern(it, fragment) }

  private fun parsePattern(pattern: Regex, fragment: String): IntRange? {
    val match = pattern.matchEntire(fragment) ?: return null
    val startLine = match.groupValues[1].toIntOrNull()?.minus(1)?.takeIf { it >= 0 } ?: return null
    val endGroup = match.groupValues.getOrElse(2) { "" }
    val endLine = if (endGroup.isEmpty()) startLine else endGroup.toIntOrNull()?.minus(1) ?: return null
    return if (endLine >= startLine) startLine..endLine else null
  }
}
