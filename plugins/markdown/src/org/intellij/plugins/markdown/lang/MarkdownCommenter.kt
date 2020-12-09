// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil

/**
 * Commenting and uncommenting lines in markdown files.
 * If the lines already have parentheses, they are escaping/unescaping when commenting/uncommenting, respectively.
 * Markdown understands comments only if there is an empty line before the commented line, so:
 *  * empty line is added if there is no empty line before it;
 *  * added empty lines are removed when uncommenting.
 *
 * Examples:
 *  1. * Simple text: Welcome to JetBrains IntelliJ IDEA.
 *     * Commented text: [comment]: <> (Welcome to JetBrains IntelliJ IDEA.)
 *
 *  2. * Text with parentheses: 1. [IntelliJ platform overview](#intellij-platform-overview)
 *     * Commented text: [comment]: <> (1. [IntelliJ platform overview]&#40;#intellij-platform-overview&#41;)
 */
class MarkdownCommenter : Commenter, SelfManagingCommenter<CommenterDataHolder> {
  private val commentPrefix = "[comment]: <> ("
  private val openRoundBracket = "("
  private val closeRoundBracket = ")"
  private val escapedOpenBracket = "&#40;"
  private val escapedCloseBracket = "&#41;"

  override fun getLineCommentPrefix(): String? = commentPrefix

  override fun getBlockCommentPrefix(): String? = null

  override fun getBlockCommentSuffix(): String? = null

  override fun getCommentedBlockCommentPrefix(): String? = commentPrefix

  override fun getCommentedBlockCommentSuffix(): String? = closeRoundBracket

  override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommenterDataHolder): String? = null

  override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommenterDataHolder): String? = null

  override fun getCommentPrefix(line: Int, document: Document, data: CommenterDataHolder): String? = commentPrefix

  override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Boolean =
    CharArrayUtil.regionMatches(document.charsSequence, offset, commentPrefix)

  override fun insertBlockComment(startOffset: Int, endOffset: Int, document: Document?, data: CommenterDataHolder?): TextRange =
    TextRange(0, 0)

  override fun commentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    val endOffset = document.getLineEndOffset(line)
    val marker = getRangeMarker(document, offset, endOffset)

    val prefix = when {
      line == 0 -> commentPrefix
      DocumentUtil.isLineEmpty(document, line - 1) -> commentPrefix
      else -> "\n$commentPrefix"
    }

    if (!DocumentUtil.isLineEmpty(document, line)) {
      escape(document, marker)

      document.insertString(marker.endOffset, closeRoundBracket)
      document.insertString(marker.startOffset, prefix)
    }

    marker.dispose()
  }

  override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    val endOffset = document.getLineEndOffset(line)
    val marker = getRangeMarker(document, offset, endOffset)

    val startOffset = when {
      line == 0 -> offset
      document.isLineModified(line - 1) -> document.getLineStartOffset(line - 1)
      else -> offset
    }

    document.deleteString(endOffset - closeRoundBracket.length, endOffset)
    document.deleteString(startOffset, offset + commentPrefix.length)

    unescape(document, marker)
    marker.dispose()
  }

  override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document?, data: CommenterDataHolder?) {}

  override fun createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): CommenterDataHolder? = null

  override fun createBlockCommentingState(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    file: PsiFile,
  ): CommenterDataHolder? = null

  override fun getBlockCommentRange(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    data: CommenterDataHolder,
  ): TextRange? = null

  private fun getRangeMarker(document: Document, startOffset: Int, endOffset: Int) = document
    .createRangeMarker(startOffset, endOffset)
    .apply {
      this.isGreedyToLeft = true
      this.isGreedyToRight = true
    }

  private fun escape(document: Document, range: RangeMarker) {
    val start = range.startOffset
    val end = range.endOffset

    if (start >= end) return

    for (i in end downTo start) {
      if (CharArrayUtil.regionMatches(document.charsSequence, i, closeRoundBracket)) {
        document.replaceString(i, i + closeRoundBracket.length, escapedCloseBracket)
      }

      if (CharArrayUtil.regionMatches(document.charsSequence, i, openRoundBracket)) {
        document.replaceString(i, i + openRoundBracket.length, escapedOpenBracket)
      }
    }
  }

  private fun unescape(document: Document, range: RangeMarker) {
    for (i in range.endOffset downTo range.startOffset) {
      if (CharArrayUtil.regionMatches(document.charsSequence, i, escapedCloseBracket)) {
        document.replaceString(i, i + escapedCloseBracket.length, closeRoundBracket)
      }

      if (CharArrayUtil.regionMatches(document.charsSequence, i, escapedOpenBracket)) {
        document.replaceString(i, i + escapedOpenBracket.length, openRoundBracket)
      }
    }
  }
}