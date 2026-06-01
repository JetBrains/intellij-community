// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.html.HTML_ATTRIBUTE_VALUES
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

internal class HtmlAttributeEnumConstValueSymbol(
  override val name: String,
  override val linkedElement: PsiElement?,
) : PsiLinkedPolySymbol {
  override val kind: PolySymbolKind
    get() = HTML_ATTRIBUTE_VALUES

  override fun createPointer(): Pointer<HtmlAttributeEnumConstValueSymbol> {
    val name = this.name
    val source = this.linkedElement?.let { SmartPointerManager.createPointer(it) }
    return Pointer<HtmlAttributeEnumConstValueSymbol> {
      val newSource = source?.dereference()
      if (newSource == null && source != null) return@Pointer null
      HtmlAttributeEnumConstValueSymbol(name, newSource)
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this
    || other is HtmlAttributeEnumConstValueSymbol
    && other.name == name
    && other.linkedElement == linkedElement

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + linkedElement.hashCode()
    return result
  }

}