// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.html.webSymbols.attributes.impl.HtmlAttributeEnumConstValueSymbol
import com.intellij.html.webSymbols.attributes.impl.WebSymbolHtmlAttributeInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PsiSourcedPolySymbol
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
/* INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbolHtmlAttributeInfo {

  val name: String

  val symbol: PolySymbol

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

  val priority: PolySymbol.Priority

  fun withName(name: String): WebSymbolHtmlAttributeInfo

  fun withSymbol(symbol: PolySymbol): WebSymbolHtmlAttributeInfo

  fun withAcceptsNoValue(acceptsNoValue: Boolean): WebSymbolHtmlAttributeInfo

  fun withAcceptsValue(acceptsValue: Boolean): WebSymbolHtmlAttributeInfo

  fun withEnumValues(enumValues: List<WebSymbolCodeCompletionItem>?): WebSymbolHtmlAttributeInfo

  fun withStrictEnumValues(strictEnumValues: Boolean): WebSymbolHtmlAttributeInfo

  fun withType(type: Any?): WebSymbolHtmlAttributeInfo

  fun withIcon(icon: Icon?): WebSymbolHtmlAttributeInfo

  fun withRequired(required: Boolean): WebSymbolHtmlAttributeInfo

  fun withDefaultValue(defaultValue: String?): WebSymbolHtmlAttributeInfo

  fun withPriority(priority: PolySymbol.Priority): WebSymbolHtmlAttributeInfo

  fun with(name: String = this.name,
           symbol: PolySymbol = this.symbol,
           acceptsNoValue: Boolean = this.acceptsNoValue,
           acceptsValue: Boolean = this.acceptsValue,
           enumValues: List<WebSymbolCodeCompletionItem>? = this.enumValues,
           strictEnumValues: Boolean = this.strictEnumValues,
           type: Any? = this.type,
           icon: Icon? = this.icon,
           required: Boolean = this.required,
           defaultValue: String? = this.defaultValue,
           priority: PolySymbol.Priority = this.priority): WebSymbolHtmlAttributeInfo

  companion object {

    @JvmStatic
    fun createEnumConstValueSymbol(origin: WebSymbolOrigin,
                                   matchedName: String,
                                   source: PsiElement?): PsiSourcedPolySymbol =
      HtmlAttributeEnumConstValueSymbol(origin, matchedName, source)

    @JvmStatic
    fun create(name: String,
               queryExecutor: WebSymbolsQueryExecutor,
               symbol: PolySymbol,
               context: PsiElement): WebSymbolHtmlAttributeInfo =
      WebSymbolHtmlAttributeInfoImpl.create(name, queryExecutor, symbol, context)

    @JvmStatic
    fun create(
      name: String,
      symbol: PolySymbol,
      acceptsNoValue: Boolean = false,
      acceptsValue: Boolean = true,
      enumValues: List<WebSymbolCodeCompletionItem>? = null,
      strictEnumValues: Boolean = false,
      type: Any? = null,
      icon: Icon? = null,
      required: Boolean = false,
      defaultValue: String? = null,
      priority: PolySymbol.Priority = PolySymbol.Priority.NORMAL
    ): WebSymbolHtmlAttributeInfo = WebSymbolHtmlAttributeInfoImpl(
      name, symbol, acceptsNoValue, acceptsValue, enumValues,
      strictEnumValues, type, icon, required, defaultValue, priority
    )

  }
}