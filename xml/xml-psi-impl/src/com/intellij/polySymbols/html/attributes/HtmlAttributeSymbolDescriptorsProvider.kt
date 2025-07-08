// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.polySymbols.html.HtmlSymbolQueryScopeContributor
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor.Companion.toAttributeDescriptor
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.hasOnlyStandardHtmlSymbols
import com.intellij.polySymbols.html.hasOnlyStandardHtmlSymbolsOrExtensions
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider

class HtmlAttributeSymbolDescriptorsProvider : XmlAttributeDescriptorsProvider {

  override fun getAttributeDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> =
    if (context == null || DumbService.isDumb(context.project) || context.containingFile !is HtmlCompatibleFile)
      XmlAttributeDescriptor.EMPTY
    else {
      val queryExecutor = PolySymbolQueryExecutorFactory.create(context)
      val additionalScope = listOf(HtmlSymbolQueryScopeContributor.HtmlContextualSymbolScope(context.firstChild))
      queryExecutor
        .listSymbolsQuery(HTML_ATTRIBUTES, expandPatterns = true)
        .exclude(PolySymbolModifier.ABSTRACT, PolySymbolModifier.VIRTUAL)
        .additionalScope(additionalScope)
        .run()
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
      val queryExecutor = PolySymbolQueryExecutorFactory.create(attribute ?: context)
      val elementDescriptor = context.descriptor
      val additionalScope = if (attribute != null)
        emptyList()
      else
        listOf(HtmlSymbolQueryScopeContributor.HtmlContextualSymbolScope(context.firstChild))

      queryExecutor
        .nameMatchQuery(HTML_ATTRIBUTES, attributeName)
        .exclude(PolySymbolModifier.ABSTRACT)
        .additionalScope(additionalScope)
        .run()
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && (elementDescriptor is HtmlElementSymbolDescriptor || !it.hasOnlyStandardHtmlSymbols())
        }
        ?.asSingleSymbol()
        ?.getAttributeDescriptor(attributeName, context, queryExecutor)
    }

  private fun PolySymbol.getAttributeDescriptor(attributeName: String, context: XmlTag, registry: PolySymbolQueryExecutor) =
    this
      .asSafely<HtmlAttributeDescriptorBasedSymbol>()
      ?.descriptor
    ?: HtmlAttributeSymbolInfo.create(attributeName, registry, this, context)
      .toAttributeDescriptor(context)

}