// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.toc

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.jetbrains.annotations.ApiStatus

/**
 * Knows the comment that keeps a header out of the generated table of contents.
 *
 * The reader accepts two spellings, both used by the Markdown All in One extension for VS Code:
 * ```markdown
 * ## Some header <!-- omit from toc -->
 *
 * <!-- omit in toc -->
 * ## Some other header
 * ```
 */
@ApiStatus.Internal
object TableOfContentsMarkers {
  /**
   * The marker to write. The reader also accepts the legacy `omit in toc` spelling.
   */
  @NlsSafe
  const val OMIT_MARKER: String = "<!-- omit from toc -->"

  private val markerRegex = Regex("""<!--\s*omit\s+(?:from|in)\s+toc\s*-->""", RegexOption.IGNORE_CASE)

  private val commentRegex = Regex("""<!--.*-->""", RegexOption.DOT_MATCHES_ALL)

  /**
   * Checks that the whole [text] is a marker comment.
   */
  fun isCommentMarker(text: CharSequence): Boolean = markerRegex.matches(text.trim())

  /**
   * Checks that [element] is an HTML comment, a marker or not.
   */
  fun isHtmlComment(element: PsiElement): Boolean = element.hasType(MarkdownTokenTypes.HTML_TAG) && commentRegex.matches(element.text)

  /**
   * Checks that [header] carries the marker comment, either at the end of the header line,
   * or on the line right above the header.
   */
  fun isOmittedFromToc(header: MarkdownHeader): Boolean = hasInlineMarker(header) || hasMarkerAbove(header)

  private fun hasInlineMarker(header: MarkdownHeader): Boolean {
    val content = header.contentElement ?: return false
    val builder = StringBuilder()
    for (child in content.children()) {
      if (child.hasType(MarkdownTokenTypes.HTML_TAG)) {
        builder.append(child.text)
        continue
      }
      if (isCommentMarker(builder)) {
        return true
      }
      builder.setLength(0)
    }
    return isCommentMarker(builder)
  }

  private fun hasMarkerAbove(header: MarkdownHeader): Boolean {
    var lineBreaks = 0
    for (sibling in header.siblings(forward = false, withSelf = false)) {
      if (sibling is PsiWhiteSpace || sibling.hasType(MarkdownTokenTypes.EOL)) {
        lineBreaks += sibling.text.count { it == '\n' }
        if (lineBreaks > 1) {
          return false
        }
        continue
      }
      return lineBreaks == 1 && isMarkerBlock(sibling)
    }
    return false
  }

  private fun isMarkerBlock(element: PsiElement): Boolean {
    if (!element.hasType(MarkdownElementTypes.HTML_BLOCK)) {
      return false
    }
    val lastLine = element.children().lastOrNull { it.hasType(MarkdownTokenTypes.HTML_BLOCK_CONTENT) } ?: return false
    return isCommentMarker(lastLine.text)
  }
}
