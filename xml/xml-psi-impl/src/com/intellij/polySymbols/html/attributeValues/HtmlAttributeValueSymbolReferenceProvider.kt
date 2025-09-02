// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributeValues

import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.HTML_ATTRIBUTE_VALUES
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue.Type
import com.intellij.polySymbols.html.htmlAttributeValue
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.asSafely

class HtmlAttributeValueSymbolReferenceProvider : PsiPolySymbolReferenceProvider<XmlAttributeValue> {
  override fun getReferencedSymbolNameOffset(psiElement: XmlAttributeValue): Int =
    psiElement.valueTextRange.startOffset - psiElement.startOffset

  override fun getReferencedSymbol(psiElement: XmlAttributeValue): PolySymbol? {
    val attribute = psiElement.parentOfType<XmlAttribute>()
    val attributeDescriptor = attribute?.descriptor?.asSafely<HtmlAttributeSymbolDescriptor>() ?: return null
    val type = attributeDescriptor.symbol.htmlAttributeValue
                 ?.takeIf { it.kind == null || it.kind == PolySymbolHtmlAttributeValue.Kind.PLAIN }
                 ?.type?.takeIf { it == Type.ENUM || it == Type.SYMBOL }
               ?: return null
    val name = psiElement.value.takeIf { it.isNotEmpty() } ?: return null
    val queryExecutor = PolySymbolQueryExecutorFactory.create(psiElement)

    return if (type == Type.ENUM)
      if (queryExecutor.codeCompletionQuery(HTML_ATTRIBUTE_VALUES, "", 0)
          .exclude(PolySymbolModifier.ABSTRACT)
          .run()
          .filter { !it.completeAfterInsert }
          .none { it.name == name })
        null
      else
        queryExecutor
          .nameMatchQuery(HTML_ATTRIBUTE_VALUES, name)
          .exclude(PolySymbolModifier.ABSTRACT)
          .run()
          .takeIf {
            it.isNotEmpty()
            && !it.hasOnlyExtensions()
          }
          ?.asSingleSymbol()
    else
      queryExecutor
        .also { it.keepUnresolvedTopLevelReferences = true }
        .nameMatchQuery(HTML_ATTRIBUTE_VALUES, name)
        .exclude(PolySymbolModifier.ABSTRACT)
        .run()
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
        }
        ?.asSingleSymbol()
      ?: PsiPolySymbolReferenceProvider.unresolvedSymbol(HTML_ATTRIBUTE_VALUES, name)
  }
}