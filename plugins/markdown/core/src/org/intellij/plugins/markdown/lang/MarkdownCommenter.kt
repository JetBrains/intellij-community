// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.codeInsight.generation.SelfManagingCommenterUtil
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil.regionMatches

/**
 * Comment and uncomment lines and selected text in Markdown files.
 *
 * Examples:
 *  1. * Simple text: Welcome to JetBrains IntelliJ IDEA.
 *     * Commented text: `<!-- Welcome to JetBrains IntelliJ IDEA. -->`
 *
 *  2. * Text with parentheses: 1. [IntelliJ platform overview](#intellij-platform-overview)
 *     * Commented text: `<!-- 1. [IntelliJ platform overview](#intellij-platform-overview) -->`
 *
 * The block comment action wraps selected text in an HTML comment.
 *
 * The line comment action writes `<!-- text -->`.
 * It writes `[//]: # (text)` when [MarkdownCommentSyntax.LINK_DEFINITION_SYNTAX_REGISTRY_KEY] is true,
 * or when the line contains `-->`.
 */
class MarkdownCommenter : Commenter, SelfManagingCommenter<CommenterDataHolder> {
  private data class CommentSyntax(val prefix: String, val suffix: String, val isLinkDefinition: Boolean = false)

  private val htmlCommentSyntax = CommentSyntax(HTML_COMMENT_PREFIX, HTML_COMMENT_SUFFIX)
  private val linkDefinitionCommentSyntax = CommentSyntax("[//]: # (", CLOSE_ROUND_BRACKET, isLinkDefinition = true)

  private val commentSyntaxes = listOf(
    htmlCommentSyntax,
    CommentSyntax(HTML_COMMENT_PREFIX, "-->"),
    CommentSyntax("<!--", HTML_COMMENT_SUFFIX),
    CommentSyntax("<!--", "-->"),
    linkDefinitionCommentSyntax,
    CommentSyntax("[//]: # $DOUBLE_QUOTE", DOUBLE_QUOTE, isLinkDefinition = true),
    CommentSyntax("[//]: # $SINGLE_QUOTE", SINGLE_QUOTE, isLinkDefinition = true),
  )

  private fun commentSyntaxToWrite(line: CharSequence? = null): CommentSyntax {
    return if (MarkdownCommentSyntax.shouldWriteLinkDefinitionSyntax() || line?.contains(HTML_COMMENT_TERMINATOR) == true) {
      linkDefinitionCommentSyntax
    }
    else {
      htmlCommentSyntax
    }
  }

  override fun getLineCommentPrefix(): String = commentSyntaxToWrite().prefix
  override fun getCommentPrefix(line: Int, document: Document, data: CommenterDataHolder): String = lineCommentPrefix

  override fun getBlockCommentPrefix(): String = HTML_COMMENT_PREFIX
  override fun getBlockCommentSuffix(): String = HTML_COMMENT_SUFFIX

  override fun getCommentedBlockCommentPrefix(): String? = null
  override fun getCommentedBlockCommentSuffix(): String? = null

  override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommenterDataHolder): String = HTML_COMMENT_PREFIX
  override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommenterDataHolder): String = HTML_COMMENT_SUFFIX

  override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Boolean {
    return getCommentSyntax(line, offset, document) != null
  }

  override fun insertBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder): TextRange? {
    if (document.charsSequence.subSequence(startOffset, endOffset).contains(HTML_COMMENT_TERMINATOR)) return null
    return SelfManagingCommenterUtil.insertBlockComment(
      startOffset, endOffset, document, HTML_COMMENT_PREFIX, HTML_COMMENT_SUFFIX,
    )
  }

  override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder) {
    SelfManagingCommenterUtil.uncommentBlockComment(
      startOffset, endOffset, document, HTML_COMMENT_PREFIX, HTML_COMMENT_SUFFIX,
    )
  }

  override fun commentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    actuallyCommentLine(line, offset, document)
  }

  override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    actuallyUncommentLine(line, offset, document)
  }

  override fun createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): CommenterDataHolder? = null

  override fun createBlockCommentingState(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    file: PsiFile,
  ): CommenterDataHolder? = null

  override fun getBlockCommentRange(selectionStart: Int, selectionEnd: Int, document: Document, data: CommenterDataHolder): TextRange? {
    return SelfManagingCommenterUtil.getBlockCommentRange(
      selectionStart, selectionEnd, document, HTML_COMMENT_PREFIX, HTML_COMMENT_SUFFIX,
    )
  }

  private fun shouldInsertEmptyLine(line: Int, document: Document): Boolean {
    return line != 0 && !DocumentUtil.isLineEmpty(document, line - 1) &&
           !isLineCommented(line - 1, document.getLineStartOffset(line - 1), document, SelfManagingCommenter.EMPTY_STATE)
  }

  private fun shouldRemoveEmptyLine(line: Int, document: Document): Boolean {
    return line != 0 && DocumentUtil.isLineEmpty(document, line - 1) && document.isLineModified(line - 1)
  }

  private fun actuallyCommentLine(line: Int, offset: Int, document: Document) {
    val end = document.getLineEndOffset(line)
    val marker = getRangeMarker(document, offset, end)
    val syntax = commentSyntaxToWrite(document.charsSequence.subSequence(offset, end))
    if (syntax.isLinkDefinition) {
      escape(document, marker)
    }
    val prefix = if (syntax.isLinkDefinition && shouldInsertEmptyLine(line, document)) "\n${syntax.prefix}" else syntax.prefix
    SelfManagingCommenterUtil.insertBlockComment(marker.startOffset, marker.endOffset, document, prefix, syntax.suffix)
    marker.dispose()
  }

  private fun actuallyUncommentLine(line: Int, offset: Int, document: Document) {
    val syntax = getCommentSyntax(line, offset, document) ?: return
    val end = document.getLineEndOffset(line)
    if (end - offset < syntax.prefix.length + syntax.suffix.length) {
      document.deleteString(offset, end)
      return
    }
    val removeEmptyLine = syntax.isLinkDefinition && shouldRemoveEmptyLine(line, document)
    val range = SelfManagingCommenterUtil.getBlockCommentRange(offset, end, document, syntax.prefix, syntax.suffix) ?: return
    val marker = when {
      removeEmptyLine -> getRangeMarker(document, document.getLineStartOffset(line - 1), range.endOffset)
      else -> getRangeMarker(document, range.startOffset, range.endOffset)
    }
    val prefix = when {
      removeEmptyLine -> "\n${syntax.prefix}"
      else -> syntax.prefix
    }
    SelfManagingCommenterUtil.uncommentBlockComment(marker.startOffset, marker.endOffset, document, prefix, syntax.suffix)
    if (syntax.isLinkDefinition) {
      unescape(document, marker)
    }
    marker.dispose()
  }

  private fun getCommentSyntax(line: Int, offset: Int, document: Document): CommentSyntax? {
    val text = document.charsSequence
    val lineEnd = document.getLineEndOffset(line)
    val lineLength = lineEnd - offset
    return commentSyntaxes.firstOrNull { syntax ->
      lineLength >= syntax.prefix.length + syntax.suffix.length &&
      regionMatches(text, offset, syntax.prefix) && regionMatches(text, lineEnd - syntax.suffix.length, syntax.suffix)
    } ?: commentSyntaxes.firstOrNull { syntax ->
      syntax.prefix == "<!--" && syntax.suffix == HTML_COMMENT_TERMINATOR &&
      ((lineLength == 5 && regionMatches(text, offset, "<!-->")) ||
       (lineLength == 6 && regionMatches(text, offset, "<!--->")))
    }
  }

  private fun getRangeMarker(document: Document, startOffset: Int, endOffset: Int): RangeMarker {
    return document.createRangeMarker(startOffset, endOffset).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
    }
  }

  private fun actuallyReplace(document: Document, offset: Int, from: String, to: String) {
    if (regionMatches(document.charsSequence, offset, from)) {
      document.replaceString(offset, offset + from.length, to)
    }
  }

  private fun unescape(document: Document, range: RangeMarker) {
    for (offset in range.endOffset downTo range.startOffset) {
      actuallyReplace(document, offset, ESCAPED_CLOSE_BRACKET, CLOSE_ROUND_BRACKET)
      actuallyReplace(document, offset, ESCAPED_OPEN_BRACKET, OPEN_ROUND_BRACKET)
    }
  }

  private fun escape(document: Document, range: RangeMarker) {
    val start = range.startOffset
    val end = range.endOffset
    if (start >= end) return
    for (offset in end downTo start) {
      actuallyReplace(document, offset, CLOSE_ROUND_BRACKET, ESCAPED_CLOSE_BRACKET)
      actuallyReplace(document, offset, OPEN_ROUND_BRACKET, ESCAPED_OPEN_BRACKET)
    }
  }

  companion object {
    private const val OPEN_ROUND_BRACKET = "("
    private const val CLOSE_ROUND_BRACKET = ")"
    private const val DOUBLE_QUOTE = "\""
    private const val SINGLE_QUOTE = "'"
    private const val ESCAPED_OPEN_BRACKET = "&#40;"
    private const val ESCAPED_CLOSE_BRACKET = "&#41;"
    private const val HTML_COMMENT_PREFIX = "<!-- "
    private const val HTML_COMMENT_SUFFIX = " -->"
    private const val HTML_COMMENT_TERMINATOR = "-->"
  }
}
