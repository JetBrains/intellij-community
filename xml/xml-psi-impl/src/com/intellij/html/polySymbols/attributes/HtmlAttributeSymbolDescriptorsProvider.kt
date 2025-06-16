// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols.attributes

import com.intellij.html.polySymbols.HtmlSymbolQueryConfigurator
import com.intellij.html.polySymbols.attributes.HtmlAttributeSymbolDescriptor.Companion.toAttributeDescriptor
import com.intellij.html.polySymbols.elements.HtmlElementSymbolDescriptor
import com.intellij.html.polySymbols.hasOnlyStandardHtmlSymbols
import com.intellij.html.polySymbols.hasOnlyStandardHtmlSymbolsOrExtensions
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
      val additionalScope = listOf(HtmlSymbolQueryConfigurator.HtmlContextualSymbolScope(context.firstChild))
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
        listOf(HtmlSymbolQueryConfigurator.HtmlContextualSymbolScope(context.firstChild))

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
      .asSafely<HtmlSymbolQueryConfigurator.HtmlAttributeDescriptorBasedSymbol>()
      ?.descriptor
    ?: HtmlAttributeSymbolInfo.create(attributeName, registry, this, context)
      .toAttributeDescriptor(context)

}