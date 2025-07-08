// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements.impl

import com.intellij.polySymbols.html.elements.HtmlElementSymbolInfo
import com.intellij.polySymbols.PolySymbol

data class HtmlElementSymbolInfoImpl(override val name: String,
                                     override val symbol: PolySymbol) : HtmlElementSymbolInfo