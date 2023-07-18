package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.suggested.startOffset

internal class HtmlAnchorSymbolDeclaration(
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
