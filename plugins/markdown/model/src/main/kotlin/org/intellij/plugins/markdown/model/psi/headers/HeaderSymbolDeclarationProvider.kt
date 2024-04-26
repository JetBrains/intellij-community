package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeaderSymbolDeclarationProvider: PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    if (element is MarkdownHeader) {
      val symbol = HeaderSymbol.createPointer(element)?.dereference() ?: return emptyList()
      return listOf(HeaderSymbolDeclaration(element, symbol))
    }
    return emptyList()
  }

  private class HeaderSymbolDeclaration(
    private val header: MarkdownHeader,
    private val symbol: HeaderSymbol
  ): PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
      return header
    }

    override fun getRangeInDeclaringElement(): TextRange {
      return TextRange(0, header.textLength)
    }

    override fun getSymbol(): Symbol {
      return symbol
    }
  }
}
