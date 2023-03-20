// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.references.WebSymbolReferenceProvider
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor

class WebSymbolInAttributeNameRefProvider : WebSymbolReferenceProvider<XmlAttribute>() {

  override fun getSymbol(psiElement: XmlAttribute): WebSymbol? =
    psiElement.descriptor
      ?.asSafely<WebSymbolAttributeDescriptor>()
      ?.symbol

  override fun shouldShowProblems(element: XmlAttribute): Boolean =
    element.parent?.descriptor !is AnyXmlElementDescriptor
}