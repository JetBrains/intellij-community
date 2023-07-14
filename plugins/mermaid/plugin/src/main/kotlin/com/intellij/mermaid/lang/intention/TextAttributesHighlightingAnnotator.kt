package com.intellij.mermaid.lang.intention

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.lang.highlighting.MermaidTextAttributes
import com.intellij.mermaid.lang.psi.MermaidSpecialState
import com.intellij.psi.PsiElement


class TextAttributesHighlightingAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidSpecialState) {
      holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
        .textAttributes(MermaidTextAttributes.identifier)
        .create()
    }
  }
}
