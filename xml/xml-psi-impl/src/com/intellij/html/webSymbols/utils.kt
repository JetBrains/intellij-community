// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WebSymbolsHtmlUtils")

package com.intellij.html.webSymbols

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.utils.unwrapMatchedSymbols

fun List<WebSymbol>.hasOnlyStandardHtmlSymbols(): Boolean =
  flatMap { it.unwrapMatchedSymbols() }
    .all { it is WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol }

fun WebSymbol.hasOnlyStandardHtmlSymbolsOrExtensions(): Boolean =
  unwrapMatchedSymbols()
    .all { it is WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol || it.extension }