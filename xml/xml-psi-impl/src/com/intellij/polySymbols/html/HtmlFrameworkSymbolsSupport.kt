// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.framework.PolySymbolFramework
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolInfo
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.elements.HtmlElementSymbolInfo
import com.intellij.psi.xml.XmlTag
import java.util.function.Predicate

interface HtmlFrameworkSymbolsSupport {

  /**
   * Provides id of the Symbol's framework, e.g. vue, angular, react, etc.
   */
  object HtmlFrameworkIdProperty : PolySymbolProperty<FrameworkId>("html-framework-id", FrameworkId::class.java)

  fun createHtmlAttributeDescriptor(info: HtmlAttributeSymbolInfo, tag: XmlTag?): HtmlAttributeSymbolDescriptor =
    HtmlAttributeSymbolDescriptor(info, tag)

  fun createHtmlElementDescriptor(info: HtmlElementSymbolInfo, tag: XmlTag): HtmlElementSymbolDescriptor =
    HtmlElementSymbolDescriptor(info, tag)

  fun getAttributeNameCodeCompletionFilter(tag: XmlTag): Predicate<String> = Predicate { true }

  /**
   * Creates an insert handler for the given attribute completion item.
   * Override this method to provide framework-specific insert handlers (e.g., braces for JSX-like syntax).
   * 
   * Default implementation returns null, which will use the standard XML attribute insert handler.
   */
  fun createAttributeInsertHandler(
    parameters: CompletionParameters,
    item: PolySymbolCodeCompletionItem,
    info: HtmlAttributeSymbolInfo
  ): InsertHandler<LookupElement>? = null

  /**
   * Determines whether to insert a value (quotes/braces) after the attribute name.
   * Override this method to customize when values should be inserted (e.g., always insert for directives).
   */
  fun shouldInsertAttributeValue(
    parameters: CompletionParameters,
    item: PolySymbolCodeCompletionItem,
    info: HtmlAttributeSymbolInfo
  ): Boolean = info.acceptsValue && !info.acceptsNoValue

  companion object {
    @JvmStatic
    fun get(symbol: PolySymbol): HtmlFrameworkSymbolsSupport =
      PolySymbolFramework.get(symbol.framework ?: "") as? HtmlFrameworkSymbolsSupport
      ?: DefaultHtmlSupport


    @JvmStatic
    fun get(id: FrameworkId?): HtmlFrameworkSymbolsSupport =
      PolySymbolFramework.get(id ?: "") as? HtmlFrameworkSymbolsSupport
      ?: DefaultHtmlSupport

  }

  private object DefaultHtmlSupport : HtmlFrameworkSymbolsSupport

}