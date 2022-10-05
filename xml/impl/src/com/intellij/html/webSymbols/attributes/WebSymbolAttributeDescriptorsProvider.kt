// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.html.webSymbols.WebSymbolsHtmlRegistryExtension
import com.intellij.html.webSymbols.WebSymbolsHtmlRegistryExtension.Companion.filterOutStandardHtmlSymbols
import com.intellij.html.webSymbols.WebSymbolsHtmlRegistryExtension.Companion.hasOnlyStandardHtmlSymbols
import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor.Companion.toAttributeDescriptor
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.WebSymbolsRegistry
import com.intellij.webSymbols.WebSymbolsRegistryManager
import com.intellij.webSymbols.utils.hasOnlyExtensions
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider

class WebSymbolAttributeDescriptorsProvider : XmlAttributeDescriptorsProvider {

  override fun getAttributeDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> =
    if (context == null || DumbService.isDumb(context.project) || context.containingFile !is HtmlCompatibleFile)
      XmlAttributeDescriptor.EMPTY
    else {
      val registry = WebSymbolsRegistryManager.get(context)
      val symbols = (context.descriptor as? WebSymbolElementDescriptor)?.symbol?.let { listOf(it) }
                    ?: registry.runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ELEMENTS, context.name))
      registry
        .runCodeCompletionQuery(listOf(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES), 0, context = symbols, virtualSymbols = false)
        .asSequence()
        .filter { it.offset == 0 && !it.completeAfterInsert }
        .filterOutStandardHtmlSymbols()
        .map { it.name }
        .distinct()
        .mapNotNull { name ->
          // TODO code completion query should return name-segments
          registry.runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES, name), strictScope = true, context = symbols)
            .filter { it.completeMatch }
            .takeIf { it.isNotEmpty() }
            ?.getAttributeDescriptor(name, context, registry)
        }
        .toList()
        .toTypedArray()
    }

  override fun getAttributeDescriptor(attributeName: String, context: XmlTag?): XmlAttributeDescriptor? =
    if (context == null || DumbService.isDumb(context.project))
      null
    else {
      val registry = WebSymbolsRegistryManager.get(context.getAttribute(attributeName) ?: context)
      val elementDescriptor = context.descriptor
      val symbols = (elementDescriptor as? WebSymbolElementDescriptor)?.symbol?.let { listOf(it) }
                    ?: registry.runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ELEMENTS, context.name))
      registry
        .runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES, attributeName), context = symbols)
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && (elementDescriptor is WebSymbolElementDescriptor || !it.hasOnlyStandardHtmlSymbols())
        }
        ?.getAttributeDescriptor(attributeName, context, registry)
    }

  private fun List<WebSymbol>.getAttributeDescriptor(attributeName: String, context: XmlTag, registry: WebSymbolsRegistry) =
    this.singleOrNull()
      ?.asSafely<WebSymbolsHtmlRegistryExtension.HtmlAttributeDescriptorBasedSymbol>()
      ?.descriptor
    ?: WebSymbolHtmlAttributeInfo.create(attributeName, registry, this)
      ?.toAttributeDescriptor(context)

}