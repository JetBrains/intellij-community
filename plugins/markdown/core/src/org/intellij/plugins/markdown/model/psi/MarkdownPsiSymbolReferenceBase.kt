package org.intellij.plugins.markdown.model.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

internal abstract class MarkdownPsiSymbolReferenceBase(
  private val element: PsiElement,
  private val rangeInElement: TextRange? = null
): MarkdownPsiSymbolReference {
  override fun getElement(): PsiElement {
    return element
  }

  override fun getRangeInElement(): TextRange {
    return rangeInElement ?: TextRange(0, element.textLength)
  }
}
