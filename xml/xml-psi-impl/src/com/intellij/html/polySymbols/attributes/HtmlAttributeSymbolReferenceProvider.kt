// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols.attributes

import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.asSafely
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor

class HtmlAttributeSymbolReferenceProvider : PsiPolySymbolReferenceProvider<XmlAttribute> {

  override fun getReferencedSymbol(psiElement: XmlAttribute): PolySymbol? =
    psiElement.descriptor
      ?.asSafely<HtmlAttributeSymbolDescriptor>()
      ?.symbol

  override fun shouldShowProblems(element: XmlAttribute): Boolean =
    element.parent?.descriptor !is AnyXmlElementDescriptor
}