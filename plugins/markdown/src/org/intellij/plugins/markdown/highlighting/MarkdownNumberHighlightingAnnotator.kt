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

    for (match in isolatedNumberRegex.findAll(element.text)) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(TextRange.create(offset + match.range.first, offset + match.range.last + 1))
        .textAttributes(MarkdownHighlighterColors.NUMBER)
        .create()
    }
  }

  companion object {
    private const val numberRegexStr = """\d(\d|_)*([.,]\d(\d|_)*)?"""
    private val isolatedNumberRegex = Regex("""(?<=(^|[^a-zA-Z\d]))(${numberRegexStr})(?=($|[^a-zA-Z\d]))""")
  }
}