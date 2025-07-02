// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor.Companion.toElementDescriptor
import com.intellij.polySymbols.html.hasOnlyStandardHtmlSymbols
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.project.DumbService
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.utils.hasOnlyExtensions
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlElementDescriptor

class HtmlElementSymbolDescriptorsProvider : XmlElementDescriptorProvider {

  override fun getDescriptor(tag: XmlTag?): XmlElementDescriptor? =
    if (tag == null || DumbService.isDumb(tag.project) || tag.containingFile !is HtmlCompatibleFile)
      null
    else {
      val queryExecutor = PolySymbolQueryExecutorFactory.create(tag)
      queryExecutor
        .nameMatchQuery(HTML_ELEMENTS, tag.name)
        .exclude(PolySymbolModifier.ABSTRACT)
        .run()
        .takeIf {
          it.isNotEmpty()
          && !it.hasOnlyExtensions()
          && !it.hasOnlyStandardHtmlSymbols()
        }
        ?.let { list ->
          HtmlElementSymbolInfo.create(tag.name, list)
            ?.toElementDescriptor(tag)
        }
    }

}