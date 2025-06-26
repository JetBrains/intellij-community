// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem

interface HtmlAttributeValueSymbolTypeSupport : PolySymbolTypeSupport {

  /**
   * @return [ThreeState.YES] if the type is equal to boolean ignoring null or undefined values,
   * [ThreeState.UNSURE] if the boolean is assignable to the type and
   * [ThreeState.NO] if boolean is not assignable to the type
   */
  fun isBoolean(symbol: PolySymbol, type: Any?, context: PsiElement): ThreeState

  fun createStringType(symbol: PolySymbol): Any?

  fun createBooleanType(symbol: PolySymbol): Any?

  fun createNumberType(symbol: PolySymbol): Any?

  fun createEnumType(symbol: PolySymbol, values: List<PolySymbolCodeCompletionItem>): Any?

  fun getEnumValues(symbol: PolySymbol, type: Any?): List<PolySymbolCodeCompletionItem>?

  fun strictEnumValues(symbol: PolySymbol, type: Any?): Boolean

}