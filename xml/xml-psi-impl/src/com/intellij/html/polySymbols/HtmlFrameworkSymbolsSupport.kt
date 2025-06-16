// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols

import com.intellij.html.polySymbols.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.html.polySymbols.attributes.HtmlAttributeSymbolInfo
import com.intellij.html.polySymbols.elements.HtmlElementSymbolDescriptor
import com.intellij.html.polySymbols.elements.HtmlElementSymbolInfo
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