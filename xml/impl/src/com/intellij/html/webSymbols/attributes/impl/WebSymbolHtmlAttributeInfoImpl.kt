// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes.impl

import com.intellij.html.webSymbols.attributes.WebSymbolHtmlAttributeInfo
import com.intellij.html.webSymbols.attributes.WebSymbolHtmlAttributeValueTypeSupport
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.utils.asSingleSymbol
import javax.swing.Icon

internal data class WebSymbolHtmlAttributeInfoImpl(
  override val name: String,
  override val symbol: WebSymbol,
  override val acceptsNoValue: Boolean,
  override val acceptsValue: Boolean,
  override val enumValues: List<WebSymbolCodeCompletionItem>?,
  override val strictEnumValues: Boolean,
  override val type: Any?,
  override val icon: Icon?,
  override val required: Boolean,
  override val defaultValue: String?,
  override val priority: WebSymbol.Priority
) : WebSymbolHtmlAttributeInfo {

  companion object {
    fun create(name: String,
               queryExecutor: WebSymbolsQueryExecutor,
               symbols: List<WebSymbol>): WebSymbolHtmlAttributeInfo? {
      val symbol = symbols.asSingleSymbol() ?: return null
      val typeSupport = symbol.origin.typeSupport as? WebSymbolHtmlAttributeValueTypeSupport
      val attrValue = symbol.attributeValue
      val kind = attrValue?.kind ?: WebSymbolHtmlAttributeValue.Kind.PLAIN
      val type = attrValue?.type ?: WebSymbolHtmlAttributeValue.Type.STRING

      val isRequired = symbol.required ?: false
      val priority = symbol.priority ?: WebSymbol.Priority.NORMAL
      val icon = symbol.icon
      val defaultValue = attrValue?.default
      val langType = if (typeSupport != null)
        when (type) {
          WebSymbolHtmlAttributeValue.Type.STRING -> typeSupport.createStringType(symbol)
          WebSymbolHtmlAttributeValue.Type.BOOLEAN -> typeSupport.createBooleanType(symbol)
          WebSymbolHtmlAttributeValue.Type.NUMBER -> typeSupport.createNumberType(symbol)
          WebSymbolHtmlAttributeValue.Type.ENUM -> {
            val valuesSymbols = queryExecutor.runNameMatchQuery(
              listOf(WebSymbol.KIND_HTML_ATTRIBUTE_VALUES), virtualSymbols = false, scope = symbols)
            typeSupport.createEnumType(symbol, valuesSymbols)
          }
          WebSymbolHtmlAttributeValue.Type.OF_MATCH -> symbol.type
          WebSymbolHtmlAttributeValue.Type.COMPLEX -> attrValue?.langType
        }?.let { typeSupport.resolve(symbol, it) }
      else null

      val isHtmlBoolean = kind == WebSymbolHtmlAttributeValue.Kind.PLAIN
                          && (type == WebSymbolHtmlAttributeValue.Type.BOOLEAN || typeSupport?.isBoolean(symbol, langType) == true)
      val valueRequired = attrValue?.required != false && !isHtmlBoolean && kind != WebSymbolHtmlAttributeValue.Kind.NO_VALUE
      val acceptsNoValue = !valueRequired || isHtmlBoolean
      val acceptsValue = kind != WebSymbolHtmlAttributeValue.Kind.NO_VALUE

      val enumValues =
        if (isHtmlBoolean) {
          listOf(WebSymbolCodeCompletionItem.create(name))
        }
        else if (kind == WebSymbolHtmlAttributeValue.Kind.PLAIN) {
          when (type) {
            WebSymbolHtmlAttributeValue.Type.ENUM -> {
              queryExecutor.runCodeCompletionQuery(listOf(WebSymbol.KIND_HTML_ATTRIBUTE_VALUES), 0, scope = symbols)
                .filter { !it.completeAfterInsert }
            }
            WebSymbolHtmlAttributeValue.Type.COMPLEX,
            WebSymbolHtmlAttributeValue.Type.OF_MATCH -> typeSupport?.getEnumValues(symbol, langType)
            else -> null
          }
        }
        else null

      val strictEnumValues = type == WebSymbolHtmlAttributeValue.Type.ENUM || typeSupport?.strictEnumValues(symbol, langType) == true

      return WebSymbolHtmlAttributeInfoImpl(name, symbol, acceptsNoValue, acceptsValue,
                                            enumValues, strictEnumValues, langType, icon, isRequired,
                                            defaultValue, priority)
    }

  }
}