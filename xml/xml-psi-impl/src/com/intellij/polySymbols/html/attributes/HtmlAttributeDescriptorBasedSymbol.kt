// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.documentation.mdn.MdnSymbolDocumentation
import com.intellij.documentation.mdn.getHtmlApiNamespace
import com.intellij.documentation.mdn.getHtmlMdnAttributeDocumentation
import com.intellij.polySymbols.html.StandardHtmlSymbol
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HTML_ATTRIBUTE_VALUES
import com.intellij.polySymbols.html.PROP_HTML_ATTRIBUTE_VALUE
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.html.HtmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.util.HtmlUtil

fun XmlAttributeDescriptor.asHtmlSymbol(tag: HtmlTag): StandardHtmlSymbol =
  HtmlAttributeDescriptorBasedSymbol(this, tag)

fun XmlAttributeDescriptor.asHtmlSymbol(tagName: String): StandardHtmlSymbol =
  HtmlAttributeDescriptorBasedSymbol(this, tagName)

internal class HtmlAttributeDescriptorBasedSymbol private constructor(
  val descriptor: XmlAttributeDescriptor,
  private val tag: HtmlTag?,
  private val tagName: String,
) : StandardHtmlSymbol() {

  constructor(descriptor: XmlAttributeDescriptor, tag: HtmlTag) : this(descriptor, tag, tag.name)

  constructor(descriptor: XmlAttributeDescriptor, tagName: String) : this(descriptor, null, tagName)

  override fun getMdnDocumentation(): MdnSymbolDocumentation? =
    getHtmlMdnAttributeDocumentation(getHtmlApiNamespace(tag?.namespace, tag, tagName),
                                     tagName, name)

  override val project: Project?
    get() = tag?.project ?: descriptor.declaration?.project

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = HTML_ATTRIBUTES

  override val name: String = descriptor.name

  override val origin: PolySymbolOrigin
    get() = PolySymbolOrigin.Companion.empty()

  override val priority: PolySymbol.Priority
    get() = PolySymbol.Priority.LOW

  override val modifiers: Set<PolySymbolModifier>
    get() = setOf(
      if (descriptor.isRequired) PolySymbolModifier.Companion.REQUIRED else PolySymbolModifier.Companion.OPTIONAL,
    )

  override val defaultValue: String?
    get() = descriptor.defaultValue

  override val source: PsiElement?
    get() = descriptor.declaration

  val attributeValue: PolySymbolHtmlAttributeValue
    get() {
      val isBooleanAttribute = HtmlUtil.isBooleanAttribute(descriptor, null)
      return PolySymbolHtmlAttributeValue.Companion.create(
        null,
        when {
          isBooleanAttribute -> PolySymbolHtmlAttributeValue.Type.BOOLEAN
          descriptor.isEnumerated -> PolySymbolHtmlAttributeValue.Type.ENUM
          else -> PolySymbolHtmlAttributeValue.Type.STRING
        },
        !isBooleanAttribute,
        descriptor.defaultValue,
        null,
      )
    }

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      PROP_HTML_ATTRIBUTE_VALUE -> property.tryCast(attributeValue)
      else -> super.get(property)
    }

  override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    if (qualifiedKind == HTML_ATTRIBUTE_VALUES && descriptor.isEnumerated)
      descriptor.enumeratedValues?.map { HtmlAttributeValueSymbol(it) } ?: emptyList()
    else
      emptyList()

  override fun createPointer(): Pointer<HtmlAttributeDescriptorBasedSymbol> {
    val descriptor = this.descriptor
    val tagPtr = tag?.createSmartPointer()
    val tagName = this.tagName
    return Pointer<HtmlAttributeDescriptorBasedSymbol> {
      val tag = tagPtr?.let { it.dereference() ?: return@Pointer null }
      HtmlAttributeDescriptorBasedSymbol(descriptor, tag, tagName)
    }
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
    other is HtmlAttributeDescriptorBasedSymbol
    && other.tag == tag
    && other.descriptor == descriptor
    && other.tagName == tagName

  override fun hashCode(): Int {
    var result = 31
    result = 31 * result + descriptor.hashCode()
    result = 31 * result + tag.hashCode()
    result = 31 * result + tagName.hashCode()
    return result
  }


  private class HtmlAttributeValueSymbol(override val name: @NlsSafe String) : PolySymbol {
    override val origin: PolySymbolOrigin
      get() = PolySymbolOrigin.empty()

    override val qualifiedKind: PolySymbolQualifiedKind
      get() = HTML_ATTRIBUTE_VALUES

    override fun createPointer(): Pointer<HtmlAttributeValueSymbol> =
      Pointer.hardPointer(this)

  }

}