// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols.elements

import com.intellij.html.polySymbols.elements.PolySymbolElementDescriptor.Companion.toElementDescriptor
import com.intellij.html.polySymbols.hasOnlyStandardHtmlSymbols
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.polySymbols.PolySymbol.Companion.HTML_ELEMENTS
import com.intellij.polySymbols.query.PolySymbolsQueryExecutorFactory
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlElementDescriptor

class PolySymbolElementDescriptorsProvider : XmlElementDescriptorProvider {

  override fun getDescriptor(tag: XmlTag?): XmlElementDescriptor? =
    if (tag == null || DumbService.isDumb(tag.project) || tag.containingFile !is HtmlCompatibleFile)
      null
    else {
      val queryExecutor = PolySymbolsQueryExecutorFactory.create(tag)
      queryExecutor
        .runNameMatchQuery(HTML_ELEMENTS, tag.name)
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && !it.hasOnlyStandardHtmlSymbols()
        }
        ?.let { list ->
          PolySymbolHtmlElementInfo.create(tag.name, list)
            ?.toElementDescriptor(tag)
        }
    }

}