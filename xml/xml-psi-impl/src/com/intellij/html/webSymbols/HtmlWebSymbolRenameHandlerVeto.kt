// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.XmlAttribute
import com.intellij.webSymbols.utils.unwrapMatchedSymbols

class HtmlWebSymbolRenameHandlerVeto : Condition<PsiElement> {

  override fun value(t: PsiElement): Boolean {
    if (t.containingFile is HtmlCompatibleFile) {
      val symbol = when (val parent = t.takeIf { it is HtmlTag || it is XmlAttribute } ?: t.parent) {
        is HtmlTag -> (parent.descriptor as? WebSymbolElementDescriptor)?.symbol
        is XmlAttribute -> (parent.descriptor as? WebSymbolAttributeDescriptor)?.symbol
        else -> null
      }
      if (symbol != null && symbol.unwrapMatchedSymbols().any {
          !it.extension && it !is WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol
        }) {
        return true
      }
    }
    return false
  }

}