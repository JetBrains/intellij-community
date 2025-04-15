// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.references.PsiWebSymbolReferenceProvider
import com.intellij.xml.util.XmlTagUtil

class WebSymbolInTagNameRefProvider : PsiWebSymbolReferenceProvider<XmlTag> {

  override fun getReferencedSymbol(psiElement: XmlTag): WebSymbol? =
    psiElement.descriptor
      ?.asSafely<WebSymbolElementDescriptor>()
      ?.symbol

  override fun getOffsetsToReferencedSymbols(psiElement: XmlTag, hints: PsiSymbolReferenceHints): Map<Int, WebSymbol> =
    getReferencedSymbol(psiElement)
      ?.let { symbol ->
        listOfNotNull(
          XmlTagUtil.getStartTagNameElement(psiElement)?.startOffsetInParent,
          XmlTagUtil.getEndTagNameElement(psiElement)?.startOffsetInParent
        ).associateBy({ it }, { symbol })
      }
    ?: emptyMap()
}
