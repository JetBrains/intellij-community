// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.html.impl.RelaxedHtmlFromSchemaElementDescriptor
import com.intellij.html.webSymbols.WebSymbolsFrameworkHtmlSupport
import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator.HtmlElementDescriptorBasedSymbol
import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol
import com.intellij.html.webSymbols.hasOnlyStandardHtmlSymbolsOrExtensions
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.impl.source.xml.XmlDescriptorUtil
import com.intellij.psi.impl.source.xml.XmlDescriptorUtil.wrapInDelegating
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.utils.nameSegments
import com.intellij.xml.*
import com.intellij.xml.impl.XmlElementDescriptorEx
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import org.jetbrains.annotations.NonNls

open class WebSymbolElementDescriptor private constructor(private val tag: XmlTag,
                                                          private val name: String,
                                                          val symbol: WebSymbol)
  : XmlElementDescriptorEx, XmlElementDescriptorAwareAboutChildren, XmlCustomElementDescriptor {

  constructor(info: WebSymbolHtmlElementInfo, tag: XmlTag) : this(tag, info.name, info.symbol)

  override fun validateTagName(tag: XmlTag, holder: ProblemsHolder, isOnTheFly: Boolean) {

  }

  fun runNameMatchQuery(namespace: SymbolNamespace,
                        kind: SymbolKind,
                        name: String,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false): List<WebSymbol> =
    WebSymbolsQueryExecutorFactory.create(tag)
      .runNameMatchQuery(listOf(WebSymbolQualifiedName(namespace, kind, name)), virtualSymbols, abstractSymbols, strictScope,
                         listOf(symbol))

  fun runListSymbolsQuery(qualifiedKind: WebSymbolQualifiedKind,
                          expandPatterns: Boolean,
                          virtualSymbols: Boolean = true,
                          abstractSymbols: Boolean = false,
                          strictScope: Boolean = false): List<WebSymbol> =
    WebSymbolsQueryExecutorFactory.create(tag)
      .runListSymbolsQuery(qualifiedKind, expandPatterns, virtualSymbols, abstractSymbols, strictScope, listOf(symbol))

  fun runCodeCompletionQuery(qualifiedKind: WebSymbolQualifiedKind,
                             name: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true): List<WebSymbolCodeCompletionItem> =
    WebSymbolsQueryExecutorFactory.create(tag)
      .runCodeCompletionQuery(qualifiedKind, name, position, virtualSymbols, listOf(symbol))

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

    fun WebSymbolHtmlElementInfo.toElementDescriptor(tag: XmlTag) =
      WebSymbolsFrameworkHtmlSupport.get(this.symbol.origin.framework)
        .createHtmlElementDescriptor(this, tag)

  }
}