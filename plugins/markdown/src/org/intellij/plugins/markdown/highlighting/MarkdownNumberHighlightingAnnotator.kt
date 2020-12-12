// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.ui.actions.styling.HighlightNumbersAction

class MarkdownNumberHighlightingAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!HighlightNumbersAction.isHighlightingEnabled || element.elementType !== MarkdownTokenTypes.TEXT) {
      return
    }

    val offset = element.startOffset

    for (match in numberRegex.findAll(element.text)) {
      if (!element.text.hasNumberIsolatedWithin(match.range)) {
        continue
      }

      val matchTextRange = TextRange.create(match.range.first, match.range.last + 1).shiftRight(offset)

      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(matchTextRange)
        .textAttributes(MarkdownHighlighterColors.NUMBER)
        .create()
    }
  }

  private fun String.hasNumberIsolatedWithin(numberRange: IntRange): Boolean {
    val first = numberRange.first
    val last = numberRange.last

    return (first == 0 || !this[first - 1].isLetter()) &&
           (last == this.lastIndex || !this[last + 1].isLetter())
  }

  companion object {
    private const val intRegexStr = """\d([\d_]*\d)?"""
    private val numberRegex = Regex("""${intRegexStr}([.,]${intRegexStr})?""")
  }
}