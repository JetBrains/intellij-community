// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.framework.PolySymbolFramework
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolInfo
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.elements.HtmlElementSymbolInfo
import com.intellij.psi.xml.XmlTag
import java.util.function.Predicate

interface HtmlFrameworkSymbolsSupport {

  fun createHtmlAttributeDescriptor(info: HtmlAttributeSymbolInfo, tag: XmlTag?): HtmlAttributeSymbolDescriptor =
    HtmlAttributeSymbolDescriptor(info, tag)

  fun createHtmlElementDescriptor(info: HtmlElementSymbolInfo, tag: XmlTag): HtmlElementSymbolDescriptor =
    HtmlElementSymbolDescriptor(info, tag)

  fun getAttributeNameCodeCompletionFilter(tag: XmlTag): Predicate<String> = Predicate { true }

  companion object {
    /**
     * Provides id of the Symbol's framework, e.g. vue, angular, react, etc.
     */
    @JvmField
    val PROP_HTML_FRAMEWORK_ID: PolySymbolProperty<FrameworkId> = PolySymbolProperty["html-framework-id"]

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