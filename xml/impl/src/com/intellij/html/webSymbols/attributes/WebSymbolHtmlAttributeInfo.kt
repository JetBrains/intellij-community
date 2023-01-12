// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.html.webSymbols.attributes.impl.HtmlAttributeEnumConstValueSymbol
import com.intellij.html.webSymbols.attributes.impl.WebSymbolHtmlAttributeInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
/* INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbolHtmlAttributeInfo {

  val name: String

  val symbol: WebSymbol

  @get:JvmName("acceptsNoValue")
  val acceptsNoValue: Boolean

  @get:JvmName("acceptsValue")
  val acceptsValue: Boolean

  val enumValues: List<WebSymbolCodeCompletionItem>?

  @get:JvmName("strictEnumValues")
  val strictEnumValues: Boolean

  val type: Any?

  val icon: Icon?

  @get:JvmName("isRequired")
  val required: Boolean

  val defaultValue: String?

  val priority: WebSymbol.Priority

  companion object {

    @JvmStatic
    fun createEnumConstValueSymbol(origin: WebSymbolOrigin,
                                   matchedName: String,
                                   source: PsiElement?): PsiSourcedWebSymbol =
      HtmlAttributeEnumConstValueSymbol(origin, matchedName, source)

    @JvmStatic
    fun create(name: String,
               queryExecutor: WebSymbolsQueryExecutor,
               symbols: List<WebSymbol>): WebSymbolHtmlAttributeInfo? =
      WebSymbolHtmlAttributeInfoImpl.create(name, queryExecutor, symbols)

  }
}