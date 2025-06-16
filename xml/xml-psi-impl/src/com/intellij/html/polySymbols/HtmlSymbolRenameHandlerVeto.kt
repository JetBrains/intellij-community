// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols

import com.intellij.html.polySymbols.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.html.polySymbols.elements.HtmlElementSymbolDescriptor
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.XmlAttribute
import com.intellij.polySymbols.utils.unwrapMatchedSymbols

class HtmlSymbolRenameHandlerVeto : Condition<PsiElement> {

  override fun value(t: PsiElement): Boolean {
    if (t.containingFile is HtmlCompatibleFile) {
      val symbol = when (val parent = t.takeIf { it is HtmlTag || it is XmlAttribute } ?: t.parent) {
        is HtmlTag -> (parent.descriptor as? HtmlElementSymbolDescriptor)?.symbol
        is XmlAttribute -> (parent.descriptor as? HtmlAttributeSymbolDescriptor)?.symbol
        else -> null
      }
      if (symbol != null && symbol.unwrapMatchedSymbols().any {
          !it.extension && it !is HtmlSymbolQueryConfigurator.StandardHtmlSymbol
        }) {
        return true
      }
    }
    return false
  }

}