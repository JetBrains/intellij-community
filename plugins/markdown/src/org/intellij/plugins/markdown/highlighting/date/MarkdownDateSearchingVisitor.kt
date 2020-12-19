// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.date

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor

internal class MarkdownDateSearchingVisitor : MarkdownRecursiveElementVisitor() {
  var dateRanges = mutableSetOf<TextRange>()

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)

    if (element.elementType !== MarkdownTokenTypes.TEXT) {
      return
    }

    val ranges = findRangesInText(element.text).map { it.shiftRight(element.startOffset) }
    dateRanges.addAll(ranges)
  }
}

private fun findRangesInText(text: String): Set<TextRange> =
  Regex("asd").findAll(text)
    .map { match ->
      TextRange.create(match.range.first, match.range.last + 1)
    }.toSet()
