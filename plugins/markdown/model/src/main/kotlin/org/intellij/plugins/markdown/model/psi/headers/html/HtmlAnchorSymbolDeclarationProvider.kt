package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue

internal class HtmlAnchorSymbolDeclarationProvider: PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    if (!element.isValid) {
      return emptyList()
    }
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
}
