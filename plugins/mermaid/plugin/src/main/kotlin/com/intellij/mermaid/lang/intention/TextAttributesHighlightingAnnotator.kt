package com.intellij.mermaid.lang.intention

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.lang.highlighting.MermaidTextAttributes
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidSpecialState
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.psi.PsiElement


class TextAttributesHighlightingAnnotator : Annotator {
  private val types = setOf(
    "int", "integer",
    "float",
    "double",
    "bool", "boolean",
    "string",
    "char", "character",
    "list",
    "array",
    "BigDecimal"
  )

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is MermaidSpecialState) {
      holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
        .textAttributes(MermaidTextAttributes.identifier)
        .create()
    } else if (element.hasType(MermaidTokens.ATTRIBUTE_WORD) && element.isTypeAttribute()) {
      holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
        .textAttributes(MermaidTextAttributes.constant)
        .create()
    }
  }

  private fun PsiElement.isTypeAttribute(): Boolean {
    return types.any { text.equals(it, ignoreCase = true) || text.equals("$it[]", ignoreCase = true) }
  }
}
