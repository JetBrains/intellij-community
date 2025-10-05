// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolInfo
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.elements.HtmlElementSymbolInfo
import com.intellij.psi.xml.XmlTag
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.framework.PolySymbolFramework
import java.util.function.Predicate

interface HtmlFrameworkSymbolsSupport {

  fun createHtmlAttributeDescriptor(info: HtmlAttributeSymbolInfo, tag: XmlTag?): HtmlAttributeSymbolDescriptor =
    HtmlAttributeSymbolDescriptor(info, tag)

  fun createHtmlElementDescriptor(info: HtmlElementSymbolInfo, tag: XmlTag): HtmlElementSymbolDescriptor =
    HtmlElementSymbolDescriptor(info, tag)

  fun getAttributeNameCodeCompletionFilter(tag: XmlTag): Predicate<String> = Predicate { true }

  companion object {
    @JvmStatic
    fun get(id: FrameworkId?): HtmlFrameworkSymbolsSupport =
      PolySymbolFramework.get(id ?: "") as? HtmlFrameworkSymbolsSupport
      ?: DefaultHtmlSupport

  }

  private object DefaultHtmlSupport : HtmlFrameworkSymbolsSupport

}