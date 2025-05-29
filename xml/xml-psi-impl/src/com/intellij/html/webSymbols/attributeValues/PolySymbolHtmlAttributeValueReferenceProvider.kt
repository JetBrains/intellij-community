package com.intellij.html.webSymbols.attributeValues

import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.asSafely
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.webSymbols.html.PolySymbolHtmlAttributeValue.Type
import com.intellij.webSymbols.query.PolySymbolsQueryExecutorFactory
import com.intellij.webSymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.webSymbols.utils.asSingleSymbol
import com.intellij.webSymbols.utils.hasOnlyExtensions

class PolySymbolHtmlAttributeValueReferenceProvider : PsiPolySymbolReferenceProvider<XmlAttributeValue> {
  override fun getReferencedSymbolNameOffset(psiElement: XmlAttributeValue): Int =
    psiElement.valueTextRange.startOffset - psiElement.startOffset

  override fun getReferencedSymbol(psiElement: XmlAttributeValue): PolySymbol? {
    val attribute = psiElement.parentOfType<XmlAttribute>()
    val attributeDescriptor = attribute?.descriptor?.asSafely<WebSymbolAttributeDescriptor>() ?: return null
    val type = attributeDescriptor.symbol.attributeValue
                 ?.takeIf { it.kind == null || it.kind == PolySymbolHtmlAttributeValue.Kind.PLAIN }
                 ?.type?.takeIf { it == Type.ENUM || it == Type.SYMBOL }
               ?: return null
    val name = psiElement.value.takeIf { it.isNotEmpty() } ?: return null
    val queryExecutor = PolySymbolsQueryExecutorFactory.create(psiElement)

    return if (type == Type.ENUM)
      if (queryExecutor.runCodeCompletionQuery(PolySymbol.HTML_ATTRIBUTE_VALUES, "", 0)
          .filter { !it.completeAfterInsert }
          .none { it.name == name })
        null
      else
        queryExecutor
          .runNameMatchQuery(PolySymbol.HTML_ATTRIBUTE_VALUES.withName(name))
          .takeIf {
            it.isNotEmpty()
            && !it.hasOnlyExtensions()
          }
          ?.asSingleSymbol()
    else
      queryExecutor
        .also { it.keepUnresolvedTopLevelReferences = true }
        .runNameMatchQuery(PolySymbol.HTML_ATTRIBUTE_VALUES.withName(name))
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
        }
        ?.asSingleSymbol()
      ?: PsiPolySymbolReferenceProvider.unresolvedSymbol(PolySymbol.HTML_ATTRIBUTE_VALUES, name)
  }
}