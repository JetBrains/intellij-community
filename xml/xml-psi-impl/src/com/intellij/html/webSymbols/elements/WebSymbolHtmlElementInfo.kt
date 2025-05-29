// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.html.webSymbols.elements.impl.WebSymbolHtmlElementInfoImpl
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.utils.asSingleSymbol

interface WebSymbolHtmlElementInfo {
  val name: String
  val symbol: PolySymbol

  companion object {
    fun create(name: String,
               symbols: List<PolySymbol>): WebSymbolHtmlElementInfo? =
      symbols.asSingleSymbol()?.let {
        WebSymbolHtmlElementInfoImpl(name, it)
      }

  }

}