// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.lang.highlighting.MermaidTextAttributes
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.MermaidStateId
import com.intellij.mermaid.lang.psi.MermaidVertex
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset


class NbspAnnotator : Annotator {
  companion object {
    val nbspRegex = Regex("&nbsp")
  }

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidNamedPsiElement) {
      val parent = element.parent ?: return
      if (parent is MermaidVertex || parent is MermaidStateId) {
        val matches = nbspRegex.findAll(element.text)
        val ranges = matches
          .map {
            val startOffset = element.startOffset + it.range.first
            val endOffset = startOffset + (it.range.last - it.range.first + 1)
            TextRange.create(startOffset, endOffset)
          }
        for (range in ranges) {
          holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(range)
            .textAttributes(MermaidTextAttributes.constant)
            .create()
        }
      }
    }
  }
}
