// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols

import com.intellij.html.polySymbols.attributes.PolySymbolAttributeDescriptor
import com.intellij.html.polySymbols.attributes.PolySymbolHtmlAttributeInfo
import com.intellij.html.polySymbols.elements.PolySymbolElementDescriptor
import com.intellij.html.polySymbols.elements.PolySymbolHtmlElementInfo
import com.intellij.psi.xml.XmlTag
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.framework.PolySymbolsFramework
import java.util.function.Predicate

interface PolySymbolsFrameworkHtmlSupport {

  fun createHtmlAttributeDescriptor(info: PolySymbolHtmlAttributeInfo, tag: XmlTag?): PolySymbolAttributeDescriptor =
    PolySymbolAttributeDescriptor(info, tag)

  fun createHtmlElementDescriptor(info: PolySymbolHtmlElementInfo, tag: XmlTag): PolySymbolElementDescriptor =
    PolySymbolElementDescriptor(info, tag)

  fun getAttributeNameCodeCompletionFilter(tag: XmlTag): Predicate<String> = Predicate { true }

  companion object {
    @JvmStatic
    fun get(id: FrameworkId?): PolySymbolsFrameworkHtmlSupport =
      PolySymbolsFramework.get(id ?: "") as? PolySymbolsFrameworkHtmlSupport
      ?: DefaultHtmlSupport

  }

  private object DefaultHtmlSupport : PolySymbolsFrameworkHtmlSupport

}