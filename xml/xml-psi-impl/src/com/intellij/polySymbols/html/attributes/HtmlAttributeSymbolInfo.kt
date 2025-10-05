// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.polySymbols.html.attributes.impl.HtmlAttributeEnumConstValueSymbol
import com.intellij.polySymbols.html.attributes.impl.HtmlAttributeSymbolInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
/* INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface HtmlAttributeSymbolInfo {

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

  fun withName(name: String): HtmlAttributeSymbolInfo

  fun withSymbol(symbol: PolySymbol): HtmlAttributeSymbolInfo

  fun withAcceptsNoValue(acceptsNoValue: Boolean): HtmlAttributeSymbolInfo

  fun withAcceptsValue(acceptsValue: Boolean): HtmlAttributeSymbolInfo

  fun withEnumValues(enumValues: List<PolySymbolCodeCompletionItem>?): HtmlAttributeSymbolInfo

  fun withStrictEnumValues(strictEnumValues: Boolean): HtmlAttributeSymbolInfo

  fun withType(type: Any?): HtmlAttributeSymbolInfo

  fun withIcon(icon: Icon?): HtmlAttributeSymbolInfo

  fun withRequired(required: Boolean): HtmlAttributeSymbolInfo

  fun withDefaultValue(defaultValue: String?): HtmlAttributeSymbolInfo

  fun withPriority(priority: PolySymbol.Priority): HtmlAttributeSymbolInfo

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
           priority: PolySymbol.Priority = this.priority): HtmlAttributeSymbolInfo

  companion object {

    @JvmStatic
    fun createEnumConstValueSymbol(origin: PolySymbolOrigin,
                                   matchedName: String,
                                   source: PsiElement?): PsiSourcedPolySymbol =
      HtmlAttributeEnumConstValueSymbol(origin, matchedName, source)

    @JvmStatic
    fun create(name: String,
               queryExecutor: PolySymbolQueryExecutor,
               symbol: PolySymbol,
               context: PsiElement): HtmlAttributeSymbolInfo =
      HtmlAttributeSymbolInfoImpl.create(name, queryExecutor, symbol, context)

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
    ): HtmlAttributeSymbolInfo = HtmlAttributeSymbolInfoImpl(
      name, symbol, acceptsNoValue, acceptsValue, enumValues,
      strictEnumValues, type, icon, required, defaultValue, priority
    )

  }
}