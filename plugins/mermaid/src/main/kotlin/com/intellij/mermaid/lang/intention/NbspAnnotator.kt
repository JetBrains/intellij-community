package com.intellij.mermaid.lang.intention

import ai.grazie.nlp.utils.length
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.lang.highlighting.MermaidTextAttributes
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.MermaidStateDiagramStatement
import com.intellij.mermaid.lang.psi.MermaidVertex
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset


class NbspAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidNamedPsiElement) {
      when (element.parent) {
        is MermaidVertex,
        is MermaidStateDiagramStatement -> {
          Regex("&nbsp")
            .findAll(element.text)
            .map {
              val startOffset = element.startOffset + it.range.first
              val endOffset = startOffset + it.range.length
              TextRange.create(startOffset, endOffset)
            }
            .forEach {
              holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
                .range(it)
                .textAttributes(MermaidTextAttributes.constant)
                .needsUpdateOnTyping()
                .create()
            }
        }
      }
    }
  }
}
