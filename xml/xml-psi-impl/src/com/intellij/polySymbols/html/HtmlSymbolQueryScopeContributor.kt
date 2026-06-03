// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.documentation.mdn.MdnSymbolDocumentation
import com.intellij.documentation.mdn.getDomEventDocumentation
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.html.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.polySymbols.html.attributes.asHtmlSymbol
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.polySymbols.html.elements.asHtmlSymbol
import com.intellij.polySymbols.js.JS_EVENTS
import com.intellij.polySymbols.query.PolySymbolCompoundScope
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryScopeContributor
import com.intellij.polySymbols.query.PolySymbolQueryScopeProviderRegistrar
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.query.polySymbolCompoundScope
import com.intellij.polySymbols.utils.match
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
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
          location.takeIf { it !is XmlTag }?.let { htmlContextualSymbolScope(it) },
          location.parentOfType<HtmlTag>(withSelf = true)?.let { StandardHtmlSymbolScope(it) },
        )
      }
  }

  private class StandardHtmlSymbolScope(private val tag: HtmlTag) : PolySymbolScope {

    override fun equals(other: Any?): Boolean =
      other is StandardHtmlSymbolScope
      && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    override fun createPointer(): Pointer<StandardHtmlSymbolScope> {
      val tag = SmartPointerManager.createPointer(this.tag)
      return Pointer {
        tag.dereference()?.let {
          StandardHtmlSymbolScope(it)
        }
      }
    }

    override fun getSymbols(
      kind: PolySymbolKind,
      params: PolySymbolListSymbolsQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> =
      if (params.queryExecutor.allowResolve) {
        when (kind) {
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
        when (qualifiedName.kind) {
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

    override val linkedElement: PsiElement?
      get() = null

    override val project: Project?
      get() = descriptor.declaration?.project

    override val kind: PolySymbolKind
      get() = JS_EVENTS

    override val name: String = descriptor.name.substring(2)

    override val priority: PolySymbol.Priority
      get() = PolySymbol.Priority.LOW

    override fun createPointer(): Pointer<HtmlEventDescriptorBasedSymbol> =
      Pointer.hardPointer(this)

    override fun equals(other: Any?): Boolean =
      other === this
      || other is HtmlEventDescriptorBasedSymbol
      && other.descriptor == descriptor

    override fun hashCode(): Int {
      return descriptor.hashCode()
    }

  }

}

@ApiStatus.Internal
fun htmlContextualSymbolScope(location: PsiElement): PolySymbolCompoundScope {
  require(location !is XmlTag) { "Cannot create HtmlContextualPolySymbolsScope on a tag." }
  return polySymbolCompoundScope {
    requiresResolve(false)
    priority(PolySymbol.Priority.HIGHEST)
    val location by dependency(location)
    initialize {
      val context = location.parentOfTypes(XmlTag::class, XmlAttribute::class)
      val htmlElement = (context as? XmlTag) ?: (context as? XmlAttribute)?.parent ?: return@initialize
      val elementScope =
        htmlElement.takeIf { queryExecutor.allowResolve }
          ?.descriptor?.asSafely<HtmlElementSymbolDescriptor>()?.symbol?.queryScope
        ?: queryExecutor.nameMatchQuery(HTML_ELEMENTS, htmlElement.name)
          .exclude(PolySymbolModifier.ABSTRACT).run().flatMap { it.queryScope }
      elementScope.forEach(::add)
      val attribute = context as? XmlAttribute ?: return@initialize
      attribute.takeIf { queryExecutor.allowResolve }
        ?.descriptor?.asSafely<HtmlAttributeSymbolDescriptor>()?.symbol?.queryScope?.forEach(::add)
        ?: queryExecutor.nameMatchQuery(HTML_ATTRIBUTES, attribute.name)
          .additionalScope(elementScope).exclude(PolySymbolModifier.ABSTRACT).run()
          .flatMap { it.queryScope }.forEach(::add)
    }
  }
}
