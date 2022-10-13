// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.html.webSymbols.attributes.WebSymbolHtmlAttributeInfo
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.html.webSymbols.elements.WebSymbolHtmlElementInfo
import com.intellij.psi.xml.XmlTag
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.framework.WebSymbolsFramework
import java.util.function.Predicate

interface WebSymbolsFrameworkHtmlSupport {

  fun createHtmlAttributeDescriptor(info: WebSymbolHtmlAttributeInfo, tag: XmlTag?): WebSymbolAttributeDescriptor =
    WebSymbolAttributeDescriptor(info, tag)

  fun createHtmlElementDescriptor(info: WebSymbolHtmlElementInfo, tag: XmlTag): WebSymbolElementDescriptor =
    WebSymbolElementDescriptor(info, tag)

  fun getAttributeNameCodeCompletionFilter(tag: XmlTag): Predicate<String> = Predicate { true }

  companion object {
    @JvmStatic
    fun get(id: FrameworkId?): WebSymbolsFrameworkHtmlSupport =
      WebSymbolsFramework.get(id ?: "") as? WebSymbolsFrameworkHtmlSupport
      ?: DefaultHtmlSupport

  }

  private object DefaultHtmlSupport : WebSymbolsFrameworkHtmlSupport

}