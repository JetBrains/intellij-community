// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.WebSymbolTypeSupport

interface WebSymbolHtmlAttributeValueTypeSupport : WebSymbolTypeSupport {

  fun resolve(symbol: WebSymbol, type: Any?): Any?

  fun isBoolean(symbol: WebSymbol, type: Any?): Boolean

  fun createStringType(symbol: WebSymbol): Any?

  fun createBooleanType(symbol: WebSymbol): Any?

  fun createNumberType(symbol: WebSymbol): Any?

  fun createEnumType(symbol: WebSymbol, values: List<WebSymbolCodeCompletionItem>): Any?

  fun getEnumValues(symbol: WebSymbol, type: Any?): List<WebSymbolCodeCompletionItem>?

  fun strictEnumValues(symbol: WebSymbol, type: Any?): Boolean

}