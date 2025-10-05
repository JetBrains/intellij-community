// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.polySymbols.html.elements.impl.HtmlElementSymbolInfoImpl
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.utils.asSingleSymbol

interface HtmlElementSymbolInfo {
  val name: String
  val symbol: PolySymbol

  companion object {
    fun create(name: String,
               symbols: List<PolySymbol>): HtmlElementSymbolInfo? =
      symbols.asSingleSymbol()?.let {
        HtmlElementSymbolInfoImpl(name, it)
      }

  }

}