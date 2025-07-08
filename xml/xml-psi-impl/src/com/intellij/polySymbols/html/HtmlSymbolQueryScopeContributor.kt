// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.documentation.mdn.*
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.attributes.asHtmlSymbol
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.elements.asHtmlSymbol
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.*
import com.intellij.polySymbols.js.JS_EVENTS
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.utils.PolySymbolPrioritizedScope
import com.intellij.polySymbols.utils.match
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.XmlAttributeDescriptor
import org.jetbrains.annotations.ApiStatus

class HtmlSymbolQueryScopeContributor : PolySymbolQueryScopeContributor {

  override fun registerProviders(registrar: PolySymbolQueryScopeProviderRegistrar) {
    registrar
      .forPsiLocation(XmlElement::class.java)
      .contributeScopeProvider { location ->
        listOfNotNull(
          location.takeIf { it !is XmlTag }?.let { HtmlContextualSymbolScope(it) },
          location.parentOfType<HtmlTag>(withSelf = true)?.let { StandardHtmlSymbolScope(it) },
        )
      }
  }

  @ApiStatus.Internal
  class HtmlContextualSymbolScope(private val location: PsiElement)
    : PolySymbolCompoundScope(), PolySymbolPrioritizedScope {

    init {
      assert(location !is XmlTag) { "Cannot create HtmlContextualPolySymbolsScope on a tag." }
    }

    override val priority: PolySymbol.Priority
      get() = PolySymbol.Priority.HIGHEST

    override fun requiresResolve(): Boolean = false

    override fun build(queryExecutor: PolySymbolQueryExecutor, consumer: (PolySymbolScope) -> Unit) {
      val context = location.parentOfTypes(XmlTag::class, XmlAttribute::class)
      val element = (context as? XmlTag) ?: (context as? XmlAttribute)?.parent ?: return
      val elementScope =
        element.takeIf { queryExecutor.allowResolve }
          ?.descriptor?.asSafely<HtmlElementSymbolDescriptor>()?.symbol?.queryScope
        ?: queryExecutor.nameMatchQuery(HTML_ELEMENTS, element.name)
          .exclude(PolySymbolModifier.ABSTRACT)
          .run()
          .flatMap { it.queryScope }

      elementScope.forEach(consumer)

      val attribute = context as? XmlAttribute ?: return
      attribute.takeIf { queryExecutor.allowResolve }
        ?.descriptor
        ?.asSafely<HtmlAttributeSymbolDescriptor>()
        ?.symbol
        ?.queryScope
        ?.forEach(consumer)
      ?: queryExecutor.nameMatchQuery(HTML_ATTRIBUTES, attribute.name)
        .additionalScope(elementScope)
        .exclude(PolySymbolModifier.ABSTRACT)
        .run()
        .flatMap { it.queryScope }
        .forEach(consumer)
    }

    override fun createPointer(): Pointer<out PolySymbolCompoundScope> {
      val attributePtr = location.createSmartPointer()
      return Pointer {
        attributePtr.dereference()?.let { HtmlContextualSymbolScope(location) }
      }
    }

    override fun equals(other: Any?): Boolean =
      other === this ||
      other is HtmlContextualSymbolScope && other.location == location

    override fun hashCode(): Int =
      location.hashCode()
  }

  private class StandardHtmlSymbolScope(private val tag: HtmlTag) : PolySymbolScope {

    override fun equals(other: Any?): Boolean =
      other is StandardHtmlSymbolScope
      && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    override fun getModificationCount(): Long = 0

    override fun createPointer(): Pointer<StandardHtmlSymbolScope> {
      val tag = SmartPointerManager.createPointer(this.tag)
      return Pointer {
        tag.dereference()?.let {
          StandardHtmlSymbolScope(it)
        }
      }
    }

    override fun getSymbols(
      qualifiedKind: PolySymbolQualifiedKind,
      params: PolySymbolListSymbolsQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> =
      if (params.queryExecutor.allowResolve) {
        when (qualifiedKind) {
          HTML_ELEMENTS ->
            (HtmlDescriptorUtils.getStandardHtmlElementDescriptor(tag)?.getElementsDescriptors(tag)
             ?: HtmlDescriptorUtils.getHtmlNSDescriptor(tag.project)?.getAllElementsDescriptors(null)
             ?: emptyArray())
              .map { it.asHtmlSymbol(tag) }
              .toList()
          HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors(tag)
              .map { it.asHtmlSymbol(tag) }
              .toList()
          JS_EVENTS ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors(tag)
              .filter { it.name.startsWith("on") }
              .map { HtmlEventDescriptorBasedSymbol(it) }
              .toList()
          else -> emptyList()
        }
      }
      else emptyList()

    override fun getMatchingSymbols(
      qualifiedName: PolySymbolQualifiedName,
      params: PolySymbolNameMatchQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> {
      if (params.queryExecutor.allowResolve) {
        when (qualifiedName.qualifiedKind) {
          HTML_ELEMENTS ->
            HtmlDescriptorUtils.getStandardHtmlElementDescriptor(tag, qualifiedName.name)
              ?.asHtmlSymbol(tag)
              ?.match(qualifiedName.name, params, stack)
              ?.let { return it }
          HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptor(tag, qualifiedName.name)
              ?.asHtmlSymbol(tag)
              ?.match(qualifiedName.name, params, stack)
              ?.let { return it }
          JS_EVENTS -> {
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptor(tag, "on${qualifiedName.name}")
              ?.let { HtmlEventDescriptorBasedSymbol(it) }
              ?.match(qualifiedName.name, params, stack)
              ?.let { return it }
          }
        }
      }
      return emptyList()
    }
  }

  private class HtmlEventDescriptorBasedSymbol(private val descriptor: XmlAttributeDescriptor) : PolySymbol, StandardHtmlSymbol() {

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getDomEventDocumentation(name)

    override val project: Project?
      get() = descriptor.declaration?.project

    override val qualifiedKind: PolySymbolQualifiedKind
      get() = JS_EVENTS

    override val name: String = descriptor.name.substring(2)

    override val origin: PolySymbolOrigin
      get() = PolySymbolOrigin.empty()

    override val priority: PolySymbol.Priority
      get() = PolySymbol.Priority.LOW

    override fun createPointer(): Pointer<HtmlEventDescriptorBasedSymbol> =
      Pointer.hardPointer(this)

  }

}

