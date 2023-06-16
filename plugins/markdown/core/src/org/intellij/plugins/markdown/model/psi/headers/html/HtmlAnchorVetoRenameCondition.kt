package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.suggested.startOffset

/**
 * Veto default rename for element attribute value.
 * Will only be active inside injected into Markdown HTML.
 */
internal class HtmlAnchorVetoRenameCondition: Condition<PsiElement> {
  override fun value(element: PsiElement): Boolean {
    val attributeValue = element.parentOfType<XmlAttributeValue>(withSelf = true)
    if (attributeValue !is XmlAttributeValue || !attributeValue.isValidAnchorAttributeValue()) {
      return false
    }
    if (!isInsideInjectedHtml(attributeValue)) {
      return false
    }
    val provider = HtmlAnchorSymbolDeclarationProvider()
    val range = attributeValue.valueTextRange.shiftLeft(attributeValue.startOffset)
    val offset = range.startOffset
    val declarations = provider.getDeclarations(attributeValue, offset)
    return declarations.filterIsInstance<HtmlAnchorSymbolDeclaration>().any()
  }
}
