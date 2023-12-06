// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.references.WebSymbolReferenceProvider
import com.intellij.xml.util.XmlTagUtil

class WebSymbolInTagNameRefProvider : WebSymbolReferenceProvider<XmlTag>() {

  override fun getSymbol(psiElement: XmlTag): WebSymbol? =
    psiElement.descriptor
      ?.asSafely<WebSymbolElementDescriptor>()
      ?.symbol

  override fun getOffsetsToSymbols(psiElement: XmlTag): Map<Int, WebSymbol> =
    getSymbol(psiElement)
      ?.let { symbol ->
        listOfNotNull(
          XmlTagUtil.getStartTagNameElement(psiElement)?.startOffsetInParent,
          XmlTagUtil.getEndTagNameElement(psiElement)?.startOffsetInParent
        ).associateBy({ it }, { symbol })
      }
    ?: emptyMap()
}
