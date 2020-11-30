// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement

class MarkdownNumberHighlightingAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is LeafPsiElement) {
      return
    }

    val offset = element.startOffset

    for (match in numberRegex.findAll(element.text)) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(TextRange.create(offset + match.range.first, offset + match.range.last + 1))
        .textAttributes(MarkdownHighlighterColors.NUMBER)
        .create()
    }
  }

  companion object {
    private val numberRegex = Regex("""\d(\d|_)*([.,]\d(\d|_)*)?""")
  }
}