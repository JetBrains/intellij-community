// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols.elements

import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.xml.util.XmlTagUtil

class HtmlElementSymbolReferenceProvider : PsiPolySymbolReferenceProvider<XmlTag> {

  override fun getReferencedSymbol(psiElement: XmlTag): PolySymbol? =
    psiElement.descriptor
      ?.asSafely<HtmlElementSymbolDescriptor>()
      ?.symbol

  override fun getOffsetsToReferencedSymbols(psiElement: XmlTag, hints: PsiSymbolReferenceHints): Map<Int, PolySymbol> =
    getReferencedSymbol(psiElement)
      ?.let { symbol ->
        listOfNotNull(
          XmlTagUtil.getStartTagNameElement(psiElement)?.startOffsetInParent,
          XmlTagUtil.getEndTagNameElement(psiElement)?.startOffsetInParent
        ).associateBy({ it }, { symbol })
      }
    ?: emptyMap()
}
