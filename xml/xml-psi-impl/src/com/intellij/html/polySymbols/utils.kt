// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PolySymbolsHtmlUtils")

package com.intellij.html.polySymbols

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.utils.unwrapMatchedSymbols

fun List<PolySymbol>.hasOnlyStandardHtmlSymbols(): Boolean =
  flatMap { it.unwrapMatchedSymbols() }
    .all { it is HtmlSymbolQueryConfigurator.StandardHtmlSymbol }

fun PolySymbol.hasOnlyStandardHtmlSymbolsOrExtensions(): Boolean =
  unwrapMatchedSymbols()
    .all { it is HtmlSymbolQueryConfigurator.StandardHtmlSymbol || it.extension }