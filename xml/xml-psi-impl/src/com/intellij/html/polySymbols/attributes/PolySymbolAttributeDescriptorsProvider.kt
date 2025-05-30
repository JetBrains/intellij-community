// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols.attributes

import com.intellij.html.polySymbols.PolySymbolsHtmlQueryConfigurator
import com.intellij.html.polySymbols.attributes.PolySymbolAttributeDescriptor.Companion.toAttributeDescriptor
import com.intellij.html.polySymbols.elements.PolySymbolElementDescriptor
import com.intellij.html.polySymbols.hasOnlyStandardHtmlSymbols
import com.intellij.html.polySymbols.hasOnlyStandardHtmlSymbolsOrExtensions
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbol.Companion.HTML_ATTRIBUTES
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.query.PolySymbolsQueryExecutorFactory
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider

class PolySymbolAttributeDescriptorsProvider : XmlAttributeDescriptorsProvider {

  override fun getAttributeDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> =
    if (context == null || DumbService.isDumb(context.project) || context.containingFile !is HtmlCompatibleFile)
      XmlAttributeDescriptor.EMPTY
    else {
      val queryExecutor = PolySymbolsQueryExecutorFactory.create(context)
      val additionalScope = listOf(PolySymbolsHtmlQueryConfigurator.HtmlContextualPolySymbolsScope(context.firstChild))
      queryExecutor
        .runListSymbolsQuery(HTML_ATTRIBUTES, expandPatterns = true, additionalScope = additionalScope, virtualSymbols = false)
        .asSequence()
        .filter { !it.hasOnlyStandardHtmlSymbolsOrExtensions() }
        .map { it.getAttributeDescriptor(it.name, context, queryExecutor) }
        .toList()
        .toTypedArray()
    }

  override fun getAttributeDescriptor(attributeName: String, context: XmlTag?): XmlAttributeDescriptor? =
    if (context == null || DumbService.isDumb(context.project))
      null
    else {
      val attribute = context.getAttribute(attributeName)
      val queryExecutor = PolySymbolsQueryExecutorFactory.create(attribute ?: context)
      val elementDescriptor = context.descriptor
      val additionalScope = if (attribute != null)
        emptyList()
      else
        listOf(PolySymbolsHtmlQueryConfigurator.HtmlContextualPolySymbolsScope(context.firstChild))

      queryExecutor
        .runNameMatchQuery(HTML_ATTRIBUTES, attributeName, additionalScope = additionalScope)
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && (elementDescriptor is PolySymbolElementDescriptor || !it.hasOnlyStandardHtmlSymbols())
        }
        ?.asSingleSymbol()
        ?.getAttributeDescriptor(attributeName, context, queryExecutor)
    }

  private fun PolySymbol.getAttributeDescriptor(attributeName: String, context: XmlTag, registry: PolySymbolsQueryExecutor) =
    this
      .asSafely<PolySymbolsHtmlQueryConfigurator.HtmlAttributeDescriptorBasedSymbol>()
      ?.descriptor
    ?: PolySymbolHtmlAttributeInfo.create(attributeName, registry, this, context)
      .toAttributeDescriptor(context)

}