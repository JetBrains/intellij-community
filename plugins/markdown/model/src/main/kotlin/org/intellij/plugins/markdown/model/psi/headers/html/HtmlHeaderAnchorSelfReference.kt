package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.model.psi.MarkdownPsiSymbolReferenceBase

internal class HtmlHeaderAnchorSelfReference(element: PsiElement): MarkdownPsiSymbolReferenceBase(element) {
  override fun resolveReference(): Collection<Symbol> {
    return emptyList()
    //val provider = HtmlAnchorSymbolDeclarationProvider()
    //val declarations = provider.getDeclarations(element, range.startOffset)
    //return declarations.map { it.symbol }
  }
}
