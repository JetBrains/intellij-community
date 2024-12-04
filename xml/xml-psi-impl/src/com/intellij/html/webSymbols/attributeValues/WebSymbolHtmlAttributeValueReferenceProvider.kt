package com.intellij.html.webSymbols.attributeValues

import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolNameSegment
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue.Type
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.references.WebSymbolReferenceProvider
import com.intellij.webSymbols.utils.asSingleSymbol
import com.intellij.webSymbols.utils.hasOnlyExtensions

class WebSymbolHtmlAttributeValueReferenceProvider : WebSymbolReferenceProvider<XmlAttributeValue>() {
  override fun getSymbolNameOffset(psiElement: XmlAttributeValue): Int =
    psiElement.valueTextRange.startOffset - psiElement.startOffset

  override fun getSymbol(psiElement: XmlAttributeValue): WebSymbol? {
    val attribute = psiElement.parentOfType<XmlAttribute>()
    val attributeDescriptor = attribute?.descriptor?.asSafely<WebSymbolAttributeDescriptor>() ?: return null
    val type = attributeDescriptor.symbol.attributeValue
                 ?.takeIf { it.kind == null || it.kind == WebSymbolHtmlAttributeValue.Kind.PLAIN }
                 ?.type?.takeIf { it == Type.ENUM || it == Type.SYMBOL }
               ?: return null
    val name = psiElement.value.takeIf { it.isNotEmpty() } ?: return null
    val queryExecutor = WebSymbolsQueryExecutorFactory.create(psiElement)

    return if (type == Type.ENUM)
      if (queryExecutor.runCodeCompletionQuery(WebSymbol.HTML_ATTRIBUTE_VALUES, "", 0)
          .filter { !it.completeAfterInsert }
          .none { it.name == name })
        null
      else
        queryExecutor
          .runNameMatchQuery(WebSymbol.HTML_ATTRIBUTE_VALUES.withName(name))
          .takeIf {
            it.isNotEmpty()
            && !it.hasOnlyExtensions()
          }
          ?.asSingleSymbol()
    else
      queryExecutor
        .runNameMatchQuery(WebSymbol.HTML_ATTRIBUTE_VALUES.withName(name))
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
        }
        ?.asSingleSymbol()
      ?: WebSymbolMatch.create(name, WebSymbol.HTML_ATTRIBUTE_VALUES, WebSymbolOrigin.empty(),
                               WebSymbolNameSegment.create(0, name.length, problem = WebSymbolNameSegment.MatchProblem.UNKNOWN_SYMBOL))
  }
}