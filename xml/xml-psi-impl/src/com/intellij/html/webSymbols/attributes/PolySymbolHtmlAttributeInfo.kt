// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.html.webSymbols.attributes.impl.HtmlAttributeEnumConstValueSymbol
import com.intellij.html.webSymbols.attributes.impl.PolySymbolHtmlAttributeInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.PsiSourcedPolySymbol
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
/* INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface PolySymbolHtmlAttributeInfo {

  val name: String

  val symbol: PolySymbol

  @get:JvmName("acceptsNoValue")
  val acceptsNoValue: Boolean

  @get:JvmName("acceptsValue")
  val acceptsValue: Boolean

  val enumValues: List<PolySymbolCodeCompletionItem>?

  @get:JvmName("strictEnumValues")
  val strictEnumValues: Boolean

  val type: Any?

  val icon: Icon?

  @get:JvmName("isRequired")
  val required: Boolean

  val defaultValue: String?

  val priority: PolySymbol.Priority

  fun withName(name: String): PolySymbolHtmlAttributeInfo

  fun withSymbol(symbol: PolySymbol): PolySymbolHtmlAttributeInfo

  fun withAcceptsNoValue(acceptsNoValue: Boolean): PolySymbolHtmlAttributeInfo

  fun withAcceptsValue(acceptsValue: Boolean): PolySymbolHtmlAttributeInfo

  fun withEnumValues(enumValues: List<PolySymbolCodeCompletionItem>?): PolySymbolHtmlAttributeInfo

  fun withStrictEnumValues(strictEnumValues: Boolean): PolySymbolHtmlAttributeInfo

  fun withType(type: Any?): PolySymbolHtmlAttributeInfo

  fun withIcon(icon: Icon?): PolySymbolHtmlAttributeInfo

  fun withRequired(required: Boolean): PolySymbolHtmlAttributeInfo

  fun withDefaultValue(defaultValue: String?): PolySymbolHtmlAttributeInfo

  fun withPriority(priority: PolySymbol.Priority): PolySymbolHtmlAttributeInfo

  fun with(name: String = this.name,
           symbol: PolySymbol = this.symbol,
           acceptsNoValue: Boolean = this.acceptsNoValue,
           acceptsValue: Boolean = this.acceptsValue,
           enumValues: List<PolySymbolCodeCompletionItem>? = this.enumValues,
           strictEnumValues: Boolean = this.strictEnumValues,
           type: Any? = this.type,
           icon: Icon? = this.icon,
           required: Boolean = this.required,
           defaultValue: String? = this.defaultValue,
           priority: PolySymbol.Priority = this.priority): PolySymbolHtmlAttributeInfo

  companion object {

    @JvmStatic
    fun createEnumConstValueSymbol(origin: PolySymbolOrigin,
                                   matchedName: String,
                                   source: PsiElement?): PsiSourcedPolySymbol =
      HtmlAttributeEnumConstValueSymbol(origin, matchedName, source)

    @JvmStatic
    fun create(name: String,
               queryExecutor: PolySymbolsQueryExecutor,
               symbol: PolySymbol,
               context: PsiElement): PolySymbolHtmlAttributeInfo =
      PolySymbolHtmlAttributeInfoImpl.create(name, queryExecutor, symbol, context)

    @JvmStatic
    fun create(
      name: String,
      symbol: PolySymbol,
      acceptsNoValue: Boolean = false,
      acceptsValue: Boolean = true,
      enumValues: List<PolySymbolCodeCompletionItem>? = null,
      strictEnumValues: Boolean = false,
      type: Any? = null,
      icon: Icon? = null,
      required: Boolean = false,
      defaultValue: String? = null,
      priority: PolySymbol.Priority = PolySymbol.Priority.NORMAL
    ): PolySymbolHtmlAttributeInfo = PolySymbolHtmlAttributeInfoImpl(
      name, symbol, acceptsNoValue, acceptsValue, enumValues,
      strictEnumValues, type, icon, required, defaultValue, priority
    )

  }
}