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
 *     * Commented text: [//]: # (Welcome to JetBrains IntelliJ IDEA.)
 *
 *  2. * Text with parentheses: 1. [IntelliJ platform overview](#intellij-platform-overview)
 *     * Commented text: [//]: # (1. [IntelliJ platform overview]&#40;#intellij-platform-overview&#41;)
 */
class MarkdownCommenter : Commenter, SelfManagingCommenter<CommenterDataHolder> {
  override fun getLineCommentPrefix(): String = commentPrefix
  override fun getCommentPrefix(line: Int, document: Document, data: CommenterDataHolder): String = lineCommentPrefix

  override fun getBlockCommentPrefix(): String? = null
  override fun getBlockCommentSuffix(): String? = null

  override fun getCommentedBlockCommentPrefix(): String? = null
  override fun getCommentedBlockCommentSuffix(): String? = null

  override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommenterDataHolder): String? = null
  override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommenterDataHolder): String? = null

  override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Boolean {
    return CharArrayUtil.regionMatches(document.charsSequence, offset, commentPrefix) &&
           CharArrayUtil.regionMatches(document.charsSequence, document.getLineEndOffset(line) - commentSuffix.length, commentSuffix)
  }

  override fun insertBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder): TextRange? = null

  override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder) = Unit

  override fun commentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    actuallyCommentLine(line, offset, document, shouldInsertEmptyLine(line, document))
  }

  override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder) {
    actuallyUncommentLine(line, offset, document, shouldRemoveEmptyLine(line, document))
  }

  override fun createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): CommenterDataHolder? = null

  override fun createBlockCommentingState(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    file: PsiFile,
  ): CommenterDataHolder? = null

  override fun getBlockCommentRange(selectionStart: Int, selectionEnd: Int, document: Document, data: CommenterDataHolder): TextRange? {
    return null
  }

  private fun shouldInsertEmptyLine(line: Int, document: Document): Boolean {
    return line != 0 && !DocumentUtil.isLineEmpty(document, line - 1) &&
           !isLineCommented(line - 1, document.getLineStartOffset(line - 1), document, SelfManagingCommenter.EMPTY_STATE)
  }

  private fun shouldRemoveEmptyLine(line: Int, document: Document): Boolean {
    return line != 0 && DocumentUtil.isLineEmpty(document, line - 1) && document.isLineModified(line - 1)
  }

  private fun actuallyCommentLine(line: Int, offset: Int, document: Document, insertEmptyLine: Boolean) {
    val end = document.getLineEndOffset(line)
    val marker = getRangeMarker(document, offset, end)
    escape(document, marker)
    val prefix = when {
      insertEmptyLine -> "\n$commentPrefix"
      else -> commentPrefix
    }
    SelfManagingCommenterUtil.insertBlockComment(marker.startOffset, marker.endOffset, document, prefix, commentSuffix)
    marker.dispose()
  }

  private fun actuallyUncommentLine(line: Int, offset: Int, document: Document, removeEmptyLine: Boolean) {
    val end = document.getLineEndOffset(line)
    val range = SelfManagingCommenterUtil.getBlockCommentRange(offset, end, document, commentPrefix, commentSuffix) ?: return
    val marker = when {
      removeEmptyLine -> getRangeMarker(document, document.getLineStartOffset(line - 1), range.endOffset)
      else -> getRangeMarker(document, range.startOffset, range.endOffset)
    }
    val prefix = when {
      removeEmptyLine -> "\n$commentPrefix"
      else -> commentPrefix
    }
    SelfManagingCommenterUtil.uncommentBlockComment(marker.startOffset, marker.endOffset, document, prefix, commentSuffix)
    unescape(document, marker)
    marker.dispose()
  }

  private fun getRangeMarker(document: Document, startOffset: Int, endOffset: Int): RangeMarker {
    return document.createRangeMarker(startOffset, endOffset).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
    }
  }

  private fun actuallyReplace(document: Document, offset: Int, from: String, to: String) {
    if (CharArrayUtil.regionMatches(document.charsSequence, offset, from)) {
      document.replaceString(offset, offset + from.length, to)
    }
  }

  private fun escape(document: Document, range: RangeMarker) {
    val start = range.startOffset
    val end = range.endOffset
    if (start >= end) {
      return
    }
    for (offset in end downTo start) {
      actuallyReplace(document, offset, closeRoundBracket, escapedCloseBracket)
      actuallyReplace(document, offset, openRoundBracket, escapedOpenBracket)
    }
  }

  private fun unescape(document: Document, range: RangeMarker) {
    for (offset in range.endOffset downTo range.startOffset) {
      actuallyReplace(document, offset, escapedCloseBracket, closeRoundBracket)
      actuallyReplace(document, offset, escapedOpenBracket, openRoundBracket)
    }
  }

  companion object {
    private const val openRoundBracket = "("
    private const val closeRoundBracket = ")"
    private const val escapedOpenBracket = "&#40;"
    private const val escapedCloseBracket = "&#41;"
    private const val commentPrefix = "[//]: # ("
    private const val commentSuffix = closeRoundBracket
  }
}
