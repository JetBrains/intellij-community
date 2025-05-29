// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WebSymbolsHtmlUtils")

package com.intellij.html.webSymbols

import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.utils.unwrapMatchedSymbols

fun List<PolySymbol>.hasOnlyStandardHtmlSymbols(): Boolean =
  flatMap { it.unwrapMatchedSymbols() }
    .all { it is PolySymbolsHtmlQueryConfigurator.StandardHtmlSymbol }

fun PolySymbol.hasOnlyStandardHtmlSymbolsOrExtensions(): Boolean =
  unwrapMatchedSymbols()
    .all { it is PolySymbolsHtmlQueryConfigurator.StandardHtmlSymbol || it.extension }