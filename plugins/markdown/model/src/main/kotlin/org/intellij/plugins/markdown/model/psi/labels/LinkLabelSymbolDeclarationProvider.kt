package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.model.psi.labels.LinkLabelSymbol.Companion.isDeclaration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LinkLabelSymbolDeclarationProvider: PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    if (element is MarkdownLinkLabel && element.isDeclaration) {
      val symbol = LinkLabelSymbol.createPointer(element)?.dereference() ?: return emptyList()
      return listOf(LinkLabelSymbolDeclaration(element, symbol))
    }
    return emptyList()
  }

  private class LinkLabelSymbolDeclaration(
    private val label: MarkdownLinkLabel,
    private val symbol: LinkLabelSymbol
  ): PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
      return label
    }

    override fun getRangeInDeclaringElement(): TextRange {
      return TextRange(0, label.textLength)
    }

    override fun getSymbol(): Symbol {
      return symbol
    }
  }
}
