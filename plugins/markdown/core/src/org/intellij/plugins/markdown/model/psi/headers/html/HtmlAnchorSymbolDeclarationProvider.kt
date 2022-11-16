package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.suggested.startOffset

internal class HtmlAnchorSymbolDeclarationProvider: PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    if (!isInsideInjectedHtml(element) || element !is XmlAttributeValue) {
      return emptyList()
    }
    if (!element.isValidAnchorAttributeValue()) {
      return emptyList()
    }
    val value = element.value
    if (value.isEmpty()) {
      return emptyList()
    }
    val host = findHostMarkdownFile(element)
    checkNotNull(host) { "Failed to find host Markdown file" }
    val range = element.valueTextRange
    val symbol = HtmlAnchorSymbol(host, range, value)
    val declaration = HtmlAnchorSymbolDeclaration(element, symbol)
    return listOf(declaration)
  }

  private class HtmlAnchorSymbolDeclaration(
    private val element: XmlAttributeValue,
    private val symbol: Symbol
  ): PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
      return element
    }

    override fun getRangeInDeclaringElement(): TextRange {
      val range = element.valueTextRange
      return range.shiftLeft(element.startOffset)
    }

    override fun getSymbol(): Symbol {
      return symbol
    }
  }
}
