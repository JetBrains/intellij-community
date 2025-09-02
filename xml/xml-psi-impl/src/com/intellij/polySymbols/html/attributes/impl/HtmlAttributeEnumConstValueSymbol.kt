// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.html.HTML_ATTRIBUTE_VALUES
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

internal class HtmlAttributeEnumConstValueSymbol(
  override val origin: PolySymbolOrigin,
  override val name: String,
  override val source: PsiElement?,
) : PsiSourcedPolySymbol {
  override val qualifiedKind: PolySymbolQualifiedKind
    get() = HTML_ATTRIBUTE_VALUES

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

}