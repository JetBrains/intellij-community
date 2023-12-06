// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements.impl

import com.intellij.html.webSymbols.elements.WebSymbolHtmlElementInfo
import com.intellij.webSymbols.WebSymbol

data class WebSymbolHtmlElementInfoImpl(override val name: String,
                                        override val symbol: WebSymbol) : WebSymbolHtmlElementInfo