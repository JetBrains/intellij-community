// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes.impl

import com.intellij.html.webSymbols.attributes.PolySymbolHtmlAttributeInfo
import com.intellij.html.webSymbols.attributes.PolySymbolHtmlAttributeValueTypeSupport
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import javax.swing.Icon

internal data class PolySymbolHtmlAttributeInfoImpl(
  override val name: String,
  override val symbol: PolySymbol,
  override val acceptsNoValue: Boolean,
  override val acceptsValue: Boolean,
  override val enumValues: List<PolySymbolCodeCompletionItem>?,
  override val strictEnumValues: Boolean,
  override val type: Any?,
  override val icon: Icon?,
  override val required: Boolean,
  override val defaultValue: String?,
  override val priority: PolySymbol.Priority
) : PolySymbolHtmlAttributeInfo {

  override fun withName(name: String): PolySymbolHtmlAttributeInfo =
    copy(name = name)

  override fun withSymbol(symbol: PolySymbol): PolySymbolHtmlAttributeInfo =
    copy(symbol = symbol)

  override fun withAcceptsNoValue(acceptsNoValue: Boolean): PolySymbolHtmlAttributeInfo =
    copy(acceptsNoValue = acceptsNoValue)

  override fun withAcceptsValue(acceptsValue: Boolean): PolySymbolHtmlAttributeInfo =
    copy(acceptsValue = acceptsValue)

  override fun withEnumValues(enumValues: List<PolySymbolCodeCompletionItem>?): PolySymbolHtmlAttributeInfo =
    copy(enumValues = enumValues)

  override fun withStrictEnumValues(strictEnumValues: Boolean): PolySymbolHtmlAttributeInfo =
    copy(strictEnumValues = strictEnumValues)

  override fun withType(type: Any?): PolySymbolHtmlAttributeInfo =
    copy(type = type)

  override fun withIcon(icon: Icon?): PolySymbolHtmlAttributeInfo =
    copy(icon = icon)

  override fun withRequired(required: Boolean): PolySymbolHtmlAttributeInfo =
    copy(required = required)

  override fun withDefaultValue(defaultValue: String?): PolySymbolHtmlAttributeInfo =
    copy(defaultValue = defaultValue)

  override fun withPriority(priority: PolySymbol.Priority): PolySymbolHtmlAttributeInfo =
    copy(priority = priority)

  override fun with(name: String,
                    symbol: PolySymbol,
                    acceptsNoValue: Boolean,
                    acceptsValue: Boolean,
                    enumValues: List<PolySymbolCodeCompletionItem>?,
                    strictEnumValues: Boolean,
                    type: Any?,
                    icon: Icon?,
                    required: Boolean,
                    defaultValue: String?,
                    priority: PolySymbol.Priority): PolySymbolHtmlAttributeInfo =
    copy(name = name,
         symbol = symbol,
         acceptsNoValue = acceptsNoValue,
         acceptsValue = acceptsValue,
         enumValues = enumValues,
         strictEnumValues = strictEnumValues,
         type = type,
         icon = icon,
         required = required,
         defaultValue = defaultValue,
         priority = priority)

  companion object {
    fun create(
      name: String,
      queryExecutor: PolySymbolsQueryExecutor,
      symbol: PolySymbol,
      context: PsiElement,
    ): PolySymbolHtmlAttributeInfo {
      val typeSupport = symbol.origin.typeSupport as? PolySymbolHtmlAttributeValueTypeSupport
      val attrValue = symbol.attributeValue
      val kind = attrValue?.kind ?: PolySymbolHtmlAttributeValue.Kind.PLAIN
      val type = attrValue?.type ?: PolySymbolHtmlAttributeValue.Type.STRING

      val isRequired = symbol.required ?: false
      val priority = symbol.priority ?: PolySymbol.Priority.NORMAL
      val icon = symbol.icon
      val defaultValue = attrValue?.default
      val langType = if (typeSupport != null)
        when (type) {
          PolySymbolHtmlAttributeValue.Type.STRING -> typeSupport.createStringType(symbol)
          PolySymbolHtmlAttributeValue.Type.BOOLEAN -> typeSupport.createBooleanType(symbol)
          PolySymbolHtmlAttributeValue.Type.NUMBER -> typeSupport.createNumberType(symbol)
          PolySymbolHtmlAttributeValue.Type.ENUM -> {
            val valuesSymbols = queryExecutor.runCodeCompletionQuery(
              PolySymbol.NAMESPACE_HTML, PolySymbol.KIND_HTML_ATTRIBUTE_VALUES, "", 0, virtualSymbols = false, additionalScope = listOf(symbol))
            typeSupport.createEnumType(symbol, valuesSymbols)
          }
          PolySymbolHtmlAttributeValue.Type.SYMBOL -> null
          PolySymbolHtmlAttributeValue.Type.OF_MATCH -> symbol.type
          PolySymbolHtmlAttributeValue.Type.COMPLEX -> attrValue?.langType
        }
      else null

      val isHtmlBoolean = if (kind == PolySymbolHtmlAttributeValue.Kind.PLAIN)
        if (type == PolySymbolHtmlAttributeValue.Type.BOOLEAN)
          ThreeState.YES
        else
          typeSupport?.isBoolean(symbol, langType, context) ?: ThreeState.YES
      else
        ThreeState.NO
      val valueRequired = attrValue?.required != false && isHtmlBoolean == ThreeState.NO && kind != PolySymbolHtmlAttributeValue.Kind.NO_VALUE
      val acceptsNoValue = !valueRequired
      val acceptsValue = kind != PolySymbolHtmlAttributeValue.Kind.NO_VALUE

      val enumValues =
        if (isHtmlBoolean == ThreeState.YES) {
          listOf(PolySymbolCodeCompletionItem.create(name))
        }
        else if (kind == PolySymbolHtmlAttributeValue.Kind.PLAIN) {
          when (type) {
            PolySymbolHtmlAttributeValue.Type.ENUM -> {
              queryExecutor.runCodeCompletionQuery(PolySymbol.NAMESPACE_HTML, PolySymbol.KIND_HTML_ATTRIBUTE_VALUES, "", 0,
                                                   additionalScope = listOf(symbol))
                .filter { !it.completeAfterInsert }
            }
            PolySymbolHtmlAttributeValue.Type.COMPLEX,
            PolySymbolHtmlAttributeValue.Type.OF_MATCH -> typeSupport?.getEnumValues(symbol, langType)
            else -> null
          }
        }
        else null

      val strictEnumValues = type == PolySymbolHtmlAttributeValue.Type.ENUM || typeSupport?.strictEnumValues(symbol, langType) == true

      return PolySymbolHtmlAttributeInfoImpl(name, symbol, acceptsNoValue, acceptsValue,
                                             enumValues, strictEnumValues, langType, icon, isRequired,
                                             defaultValue, priority)
    }

  }
}