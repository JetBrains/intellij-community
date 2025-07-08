// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.util.Condition
import com.intellij.polySymbols.utils.unwrapMatchedSymbols
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.XmlAttribute

class HtmlSymbolRenameHandlerVeto : Condition<PsiElement> {

  override fun value(t: PsiElement): Boolean {
    if (t.containingFile is HtmlCompatibleFile) {
      val symbol = when (val parent = t.takeIf { it is HtmlTag || it is XmlAttribute } ?: t.parent) {
        is HtmlTag -> (parent.descriptor as? HtmlElementSymbolDescriptor)?.symbol
        is XmlAttribute -> (parent.descriptor as? HtmlAttributeSymbolDescriptor)?.symbol
        else -> null
      }
      if (symbol != null && symbol.unwrapMatchedSymbols().any {
          !it.extension && it !is StandardHtmlSymbol
        }) {
        return true
      }
    }
    return false
  }

}