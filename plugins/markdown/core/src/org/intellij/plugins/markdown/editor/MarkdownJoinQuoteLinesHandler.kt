// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownJoinQuoteLinesHandler : JoinRawLinesHandlerDelegate {
  private val quotePrefixRegex = "[ \\t]*+(?:>[ \\t]*+)+".toRegex()

  override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int =
    JoinLinesHandlerDelegate.CANNOT_JOIN

  override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
    if (file !is MarkdownFile || end >= document.textLength) {
      return JoinLinesHandlerDelegate.CANNOT_JOIN
    }

    val line = document.getLineNumber(start)
    val text = document.charsSequence
    val firstPrefix = findQuotePrefix(text, document.getLineStartOffset(line)) ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
    val secondPrefix = findQuotePrefix(text, end) ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
    if (firstPrefix.trim() != secondPrefix.trim()) return start

    document.replaceString(start, end + secondPrefix.length, " ")
    return start
  }

  private fun findQuotePrefix(text: CharSequence, start: Int): String? =
    quotePrefixRegex.matchAt(text, start)?.value
}
