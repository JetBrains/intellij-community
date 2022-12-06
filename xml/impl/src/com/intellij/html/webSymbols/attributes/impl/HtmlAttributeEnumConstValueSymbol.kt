// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes.impl

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.webSymbols.*

internal class HtmlAttributeEnumConstValueSymbol(override val origin: WebSymbolOrigin,
                                                 override val name: String,
                                                 override val source: PsiElement?) : PsiSourcedWebSymbol {
  override val kind: SymbolKind
    get() = WebSymbol.KIND_HTML_ATTRIBUTE_VALUES

  override fun createPointer(): Pointer<HtmlAttributeEnumConstValueSymbol> {
    val origin = this.origin
    val name = this.name
    val source = this.source?.let { SmartPointerManager.createPointer(it) }
    return Pointer<HtmlAttributeEnumConstValueSymbol> {
      val newSource = source?.dereference()
      if (newSource == null && source != null) return@Pointer null
      HtmlAttributeEnumConstValueSymbol(origin, name, newSource)
    }
  }

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_HTML

}