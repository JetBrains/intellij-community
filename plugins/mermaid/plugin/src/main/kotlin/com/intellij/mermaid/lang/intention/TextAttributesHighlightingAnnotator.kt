package com.intellij.mermaid.lang.intention

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.lang.highlighting.MermaidTextAttributes
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.psi.MermaidSpecialState
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet


class TextAttributesHighlightingAnnotator : Annotator {
  private val types = setOf(
    "int", "integer", "bigint", "biginteger",
    "float",
    "double",
    "bool", "boolean",
    "string", "text",
    "char", "character",
    "varchar",
    "list",
    "array",
    "BigDecimal",
    "numeric",
    "timestamp"
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
    } else if (element.hasType(TokenSet.create(MermaidElements.IDENTIFYING_QUOTED_SANKEY_FIELD_VALUE, MermaidElements.IDENTIFYING_COMPLEX_SANKEY_TEXT))) {
      holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
        .textAttributes(MermaidTextAttributes.identifier)
        .create()
    }
  }

  private fun PsiElement.isTypeAttribute(): Boolean {
    return types.any {
      text.equals(it, ignoreCase = true)
        || text.startsWith("$it[", ignoreCase = true)
        || text.startsWith("$it(", ignoreCase = true)
        || text.startsWith("$it{", ignoreCase = true)
    }
  }
}
