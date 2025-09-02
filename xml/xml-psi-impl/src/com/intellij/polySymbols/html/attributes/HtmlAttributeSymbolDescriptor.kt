// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.polySymbols.html.HtmlFrameworkSymbolsSupport
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.utils.unwrapMatchedSymbols
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.meta.PsiPresentableMetaData
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ArrayUtil
import com.intellij.xml.impl.BasicXmlAttributeDescriptor
import com.intellij.xml.impl.XmlAttributeDescriptorEx
import javax.swing.Icon

open class HtmlAttributeSymbolDescriptor private constructor(
  val tag: XmlTag?,
  private val name: String,
  val symbol: PolySymbol,
  private val acceptsNoValue: Boolean,
  private val acceptsValue: Boolean,
  private val enumValues: List<PolySymbolCodeCompletionItem>?,
  private val strictEnumValues: Boolean,
  val type: Any?,
  private val icon: Icon?,
  private val isRequired: Boolean,
  private val defaultValue: String?,
)
  : BasicXmlAttributeDescriptor(), XmlAttributeDescriptorEx, PsiPresentableMetaData {

  constructor(info: HtmlAttributeSymbolInfo, tag: XmlTag?)
    : this(tag, info.name, info.symbol, info.acceptsNoValue, info.acceptsValue, info.enumValues,
           info.strictEnumValues, info.type, info.icon, info.required, info.defaultValue)

  private val supportsEnums = enumValues != null

  override fun getIcon(): Icon? = icon
  override fun isRequired(): Boolean = isRequired
  override fun getName(): String = name
  override fun getDeclaration(): PsiElement? = null
  override fun getDeclarations(): Collection<PsiElement> =
    emptyList()

  override fun getDefaultValue(): String? = defaultValue

  override fun validateValue(context: XmlElement?, value: String?): String? =
    when {
      value == null -> null
      !acceptsValue -> PolySymbolsBundle.message("web.inspection.message.attribute.does.not.accept.value", name)
      supportsEnums && strictEnumValues -> {
        val match = matchEnum(value)
        if (match.isEmpty()) {
          PolySymbolsBundle.message(
            "web.inspection.message.attribute.value.no.valid",
            value, name, StringUtil.shortenTextWithEllipsis(NlsMessages.formatOrList(enumValues!!.map { it.name }), 500, 50))
        }
        else {
          null
        }
      }
      else -> null
    }

  override fun validateAttributeName(attribute: XmlAttribute, holder: ProblemsHolder, isOnTheFly: Boolean) {

  }

  override fun isEnumerated(): Boolean = acceptsNoValue || supportsEnums

  override fun getEnumeratedValueDeclaration(xmlElement: XmlElement?, value: String?): PsiElement? =
    if (acceptsNoValue && value.isNullOrEmpty())
      xmlElement
    else if (supportsEnums && value != null) {
      val matches = matchEnum(value)
      if (matches.isEmpty()) {
        if (strictEnumValues) null
        else super.getEnumeratedValueDeclaration(xmlElement, value)
      }
      else matches.firstNotNullOfOrNull { (it.symbol as? PsiSourcedPolySymbol)?.source } ?: xmlElement
    }
    else if (value.isNullOrEmpty())
      null
    else
      super.getEnumeratedValueDeclaration(xmlElement, value)

  override fun getDefaultValueDeclaration(): PsiElement? =
    if (defaultValue != null && supportsEnums) {
      matchEnum(defaultValue).firstNotNullOfOrNull { (it.symbol as? PsiSourcedPolySymbol)?.source }
    }
    else super.getDefaultValueDeclaration()

  override fun getEnumeratedValues(): Array<out String> =
    if (supportsEnums)
      enumValues!!.map { it.name }
        .let { if (acceptsNoValue) it.plus("") else it }
        .toTypedArray()
    else if (acceptsNoValue)
      arrayOf("", name)
    else ArrayUtil.EMPTY_STRING_ARRAY

  override fun getValueReferences(element: XmlElement?, text: String): Array<PsiReference> =
    if (supportsEnums && matchEnum(text).isNotEmpty())
      super.getValueReferences(element, text)
    else
      PsiReference.EMPTY_ARRAY

  override fun init(element: PsiElement?) {}

  override fun getTypeName(): String? = null

  override fun isFixed(): Boolean = false

  override fun hasIdType(): Boolean =
    symbol.unwrapMatchedSymbols()
      .filterIsInstance<HtmlAttributeDescriptorBasedSymbol>()
      .any { it.descriptor.hasIdType() }

  override fun hasIdRefType(): Boolean =
    symbol.unwrapMatchedSymbols()
      .filterIsInstance<HtmlAttributeDescriptorBasedSymbol>()
      .any { it.descriptor.hasIdRefType() }

  private fun matchEnum(value: String): List<PolySymbolCodeCompletionItem> =
    enumValues!!.filter { it.name == value }

  companion object {

    fun HtmlAttributeSymbolInfo.toAttributeDescriptor(tag: XmlTag?): HtmlAttributeSymbolDescriptor =
      HtmlFrameworkSymbolsSupport.get(this.symbol.origin.framework)
        .createHtmlAttributeDescriptor(this, tag)

  }

}