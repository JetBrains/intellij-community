// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor.Companion.toElementDescriptor
import com.intellij.html.webSymbols.hasOnlyStandardHtmlSymbols
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlTag
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.utils.hasOnlyExtensions
import com.intellij.xml.XmlElementDescriptor

class WebSymbolElementDescriptorsProvider : XmlElementDescriptorProvider {

  override fun getDescriptor(tag: XmlTag?): XmlElementDescriptor? =
    if (tag == null || DumbService.isDumb(tag.project) || tag.containingFile !is HtmlCompatibleFile)
      null
    else {
      val queryExecutor = WebSymbolsQueryExecutorFactory.create(tag)
      queryExecutor
        .runNameMatchQuery(NAMESPACE_HTML, KIND_HTML_ELEMENTS, tag.name)
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && !it.hasOnlyStandardHtmlSymbols()
        }
        ?.let { list ->
          WebSymbolHtmlElementInfo.create(tag.name, list)
            ?.toElementDescriptor(tag)
        }
    }

}