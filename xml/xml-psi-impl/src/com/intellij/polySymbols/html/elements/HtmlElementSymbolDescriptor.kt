// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.html.impl.RelaxedHtmlFromSchemaElementDescriptor
import com.intellij.polySymbols.html.HtmlFrameworkSymbolsSupport
import com.intellij.polySymbols.html.StandardHtmlSymbol
import com.intellij.polySymbols.html.hasOnlyStandardHtmlSymbolsOrExtensions
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.utils.nameSegments
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.impl.source.xml.XmlDescriptorUtil
import com.intellij.psi.impl.source.xml.XmlDescriptorUtil.wrapInDelegating
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.*
import com.intellij.xml.impl.XmlElementDescriptorEx
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import org.jetbrains.annotations.NonNls

open class HtmlElementSymbolDescriptor private constructor(
  private val tag: XmlTag,
  private val name: String,
  val symbol: PolySymbol,
) : XmlElementDescriptorEx, XmlElementDescriptorAwareAboutChildren, XmlCustomElementDescriptor {

  constructor(info: HtmlElementSymbolInfo, tag: XmlTag) : this(tag, info.name, info.symbol)

  override fun validateTagName(tag: XmlTag, holder: ProblemsHolder, isOnTheFly: Boolean) {

  }

  fun runNameMatchQuery(
    qualifiedName: PolySymbolQualifiedName,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
  ): List<PolySymbol> =
    PolySymbolQueryExecutorFactory.create(tag)
      .nameMatchQuery(listOf(qualifiedName)) {
        strictScope(strictScope)
        if (!virtualSymbols) exclude(PolySymbolModifier.VIRTUAL)
        if (!abstractSymbols) exclude(PolySymbolModifier.ABSTRACT)
        additionalScope(symbol.queryScope)
      }

  fun runListSymbolsQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
  ): List<PolySymbol> =
    PolySymbolQueryExecutorFactory.create(tag)
      .listSymbolsQuery(qualifiedKind, expandPatterns) {
        strictScope(strictScope)
        if (!virtualSymbols) exclude(PolySymbolModifier.VIRTUAL)
        if (!abstractSymbols) exclude(PolySymbolModifier.ABSTRACT)
        additionalScope(symbol.queryScope)
      }

  fun runCodeCompletionQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    virtualSymbols: Boolean = true,
  ): List<PolySymbolCodeCompletionItem> =
    PolySymbolQueryExecutorFactory.create(tag)
      .codeCompletionQuery(qualifiedKind, name, position) {
        if (!virtualSymbols) exclude(PolySymbolModifier.VIRTUAL)
        exclude(PolySymbolModifier.ABSTRACT)
        additionalScope(symbol.queryScope)
      }

  override fun getQualifiedName(): String {
    return name
  }

  override fun getDefaultName(): String {
    return name
  }

  override fun getName(context: PsiElement): String {
    return name
  }

  override fun getName(): String {
    return name
  }

  override fun getElementsDescriptors(context: XmlTag): Array<XmlElementDescriptor> =
    getStandardHtmlElementDescriptor()
      ?.getElementsDescriptors(context)
      ?.map { wrapInDelegating(it) }
      ?.toTypedArray()
    ?: XmlDescriptorUtil.getElementsDescriptors(context)

  override fun getElementDescriptor(childTag: XmlTag, contextTag: XmlTag): XmlElementDescriptor? =
    getStandardHtmlElementDescriptor()
      ?.getElementDescriptor(childTag, contextTag)
      ?.let { if (it !is AnyXmlElementDescriptor) wrapInDelegating(it) else it }

  override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> =
    getStandardHtmlElementDescriptor()
      ?.getAttributesDescriptors(context)
    ?: tag.getNSDescriptor(tag.namespace, false)
      ?.let { nsDescriptor ->
        if (nsDescriptor is HtmlNSDescriptorImpl) {
          nsDescriptor.getElementDescriptorByName(tag.localName)
          ?: nsDescriptor.getElementDescriptorByName("div")
          ?: nsDescriptor.getElementDescriptorByName("span")
        }
        else
          nsDescriptor.getElementDescriptor(tag)
      }
      ?.getAttributesDescriptors(context)
    ?: XmlAttributeDescriptor.EMPTY

  override fun getAttributeDescriptor(attribute: XmlAttribute): XmlAttributeDescriptor? {
    return getAttributeDescriptor(attribute.name, attribute.parent)
  }

  override fun getAttributeDescriptor(@NonNls attributeName: String, context: XmlTag?): XmlAttributeDescriptor? {
    return RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attributeName, context)
  }

  override fun getNSDescriptor(): XmlNSDescriptor? {
    return null
  }

  override fun getTopGroup(): XmlElementsGroup? {
    return null
  }

  override fun getContentType(): Int {
    return XmlElementDescriptor.CONTENT_TYPE_ANY
  }

  override fun getDefaultValue(): String? {
    return null
  }

  override fun getDeclaration(): PsiElement {
    return tag
  }

  override fun allowElementsFromNamespace(namespace: String, context: XmlTag): Boolean =
    (getStandardHtmlElementDescriptor() as? XmlElementDescriptorAwareAboutChildren)
      ?.allowElementsFromNamespace(namespace, context)
    ?: true

  override fun init(element: PsiElement) {}

  override fun isCustomElement(): Boolean =
    !symbol.hasOnlyStandardHtmlSymbolsOrExtensions()

  private fun getStandardHtmlElementDescriptor() =
    ((symbol as? StandardHtmlSymbol)
     ?: symbol.nameSegments.singleOrNull()
       ?.symbols
       ?.filterIsInstance<StandardHtmlSymbol>()
       ?.firstOrNull())
      ?.asSafely<HtmlElementDescriptorBasedSymbol>()
      ?.descriptor

  companion object {

    fun HtmlElementSymbolInfo.toElementDescriptor(tag: XmlTag): HtmlElementSymbolDescriptor =
      HtmlFrameworkSymbolsSupport.get(this.symbol.origin.framework)
        .createHtmlElementDescriptor(this, tag)

  }
}