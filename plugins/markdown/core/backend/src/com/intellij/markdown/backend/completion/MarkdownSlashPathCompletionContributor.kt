// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAtPath
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph

internal class MarkdownSlashPathCompletionContributor : MarkdownPathCompletionContributor() {
  override fun findPath(parameters: CompletionParameters): PathCompletionContext? {
    val elementAtCaret = findElementAtCaret(parameters)
    if (isUnsupportedContext(elementAtCaret)) return null

    val document = parameters.editor.document
    val lineStart = document.getLineStartOffset(document.getLineNumber(parameters.offset))
    val linePrefix = document.charsSequence.subSequence(lineStart, parameters.offset).toString()
    val pathStart = findPathStart(linePrefix) ?: return null
    val pathPrefix = linePrefix.substring(pathStart)
    val insertionPrefix = if (pathPrefix.startsWith("./")) "./" else "/"
    return PathCompletionContext(
      pathPrefix = pathPrefix.removePrefix(insertionPrefix),
      startOffset = lineStart + pathStart,
      insertionPrefix = insertionPrefix,
    )
  }

  private fun isUnsupportedContext(element: PsiElement): Boolean {
    return PsiTreeUtil.getParentOfType(element, MarkdownParagraph::class.java) == null ||
           PsiTreeUtil.getParentOfType(element, MarkdownCodeSpan::class.java) != null ||
           PsiTreeUtil.getParentOfType(element, MarkdownAtPath::class.java) != null
  }

  private fun findPathStart(text: String): Int? {
    var slashIndex = text.lastIndexOf('/')
    while (slashIndex >= 0) {
      val pathStart = if (slashIndex > 0 && text[slashIndex - 1] == '.') slashIndex - 1 else slashIndex
      if (isPathStart(text, pathStart)) return pathStart
      slashIndex = text.lastIndexOf('/', slashIndex - 1)
    }
    return null
  }

  private fun isPathStart(text: String, pathStart: Int): Boolean {
    if (pathStart == 0) return true
    val previous = text[pathStart - 1]
    return !previous.isLetterOrDigit() && previous !in "_-.:/"
  }
}
