// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.documentation.mdn.*
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.asSafely
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItemCustomizer
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryConfigurator
import com.intellij.webSymbols.utils.match
import com.intellij.webSymbols.utils.unwrapMatchedSymbols
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import com.intellij.xml.util.HtmlUtil
import com.intellij.xml.util.XmlUtil.HTML_URI
import java.util.*

class WebSymbolsHtmlQueryConfigurator : WebSymbolsQueryConfigurator {

  override fun getScope(project: Project,
                        location: PsiElement?,
                        context: WebSymbolsContext,
                        allowResolve: Boolean): List<WebSymbolsScope> =
    ((location as? XmlAttribute)?.parent ?: location as? XmlTag)?.let {
      listOf(StandardHtmlSymbolsScope(it))
    }
    ?: emptyList()

  companion object {

    @JvmStatic
    fun getHtmlNSDescriptor(project: Project): HtmlNSDescriptorImpl? {
      return CachedValuesManager.getManager(project).getCachedValue(project) {
        val descriptor = XmlElementFactory.getInstance(project)
          .createTagFromText("<div>")
          .getNSDescriptor(HTML_URI, true)
        if (descriptor !is HtmlNSDescriptorImpl) {
          return@getCachedValue CachedValueProvider.Result.create<HtmlNSDescriptorImpl>(null, ModificationTracker.EVER_CHANGED)
        }
        CachedValueProvider.Result.create(descriptor, descriptor.getDescriptorFile()!!)
      }
    }

    @JvmStatic
    fun getStandardHtmlAttributeDescriptors(tag: XmlTag): Sequence<XmlAttributeDescriptor> =
      getHtmlElementDescriptor(tag)
        ?.getDefaultAttributeDescriptors(tag)
        ?.asSequence()
        ?.filter { !it.getName(tag).contains(':') }
      ?: emptySequence()

    @JvmStatic
    fun getStandardHtmlAttributeDescriptor(tag: XmlTag, attrName: String): XmlAttributeDescriptor? =
      getHtmlElementDescriptor(tag)
        ?.getDefaultAttributeDescriptor(attrName.adjustCase(tag), tag)
        ?.takeIf { !it.getName(tag).contains(':') }

    private fun getHtmlElementDescriptor(tag: XmlTag): HtmlElementDescriptorImpl? =
      when (val tagDescriptor = tag.descriptor) {
        is HtmlElementDescriptorImpl -> tagDescriptor
        is WebSymbolElementDescriptor, is AnyXmlElementDescriptor -> {
          getStandardHtmlElementDescriptor(tag)
          ?: getStandardHtmlElementDescriptor(tag, "div")
        }
        else -> null
      }

    private fun getStandardHtmlElementDescriptor(tag: XmlTag, name: String = tag.localName): HtmlElementDescriptorImpl? {
      val parentTag = tag.parentTag
      if (parentTag != null) {
        parentTag.getNSDescriptor(parentTag.namespace, false)
          .asSafely<HtmlNSDescriptorImpl>()
          ?.getElementDescriptorByName(parentTag.localName.adjustCase(tag))
          ?.asSafely<HtmlElementDescriptorImpl>()
          ?.getElementDescriptor(name.adjustCase(tag), parentTag)
          ?.asSafely<HtmlElementDescriptorImpl>()
          ?.let { return it }
      }
      return getHtmlNSDescriptor(tag.project)
        ?.getElementDescriptorByName(name.adjustCase(tag))
        ?.asSafely<HtmlElementDescriptorImpl>()
    }

    private fun String.adjustCase(tag: XmlTag) =
      if (tag.isCaseSensitive) this else StringUtil.toLowerCase(this)

    fun List<WebSymbol>.hasOnlyStandardHtmlSymbols(): Boolean =
      flatMap { it.unwrapMatchedSymbols() }
        .all { it is StandardHtmlSymbol }

    fun WebSymbol.hasOnlyStandardHtmlSymbolsOrExtensions(): Boolean =
      unwrapMatchedSymbols()
        .all { it is StandardHtmlSymbol || it.extension }

  }

  class HtmlSymbolsCodeCompletionItemCustomizer : WebSymbolCodeCompletionItemCustomizer {
    override fun customize(item: WebSymbolCodeCompletionItem,
                           framework: FrameworkId?,
                           qualifiedKind: WebSymbolQualifiedKind,
                           location: PsiElement): WebSymbolCodeCompletionItem =
      when (qualifiedKind) {
        WebSymbol.HTML_ELEMENTS -> item.withTypeText(item.symbol?.origin?.library)
        WebSymbol.HTML_ATTRIBUTES -> item // TODO - we can figure out the actual type with full match provided
        else -> item
      }
  }

  private class StandardHtmlSymbolsScope(private val tag: XmlTag) : WebSymbolsScope {

    override fun equals(other: Any?): Boolean =
      other is StandardHtmlSymbolsScope
      && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    override fun getModificationCount(): Long = 0

    override fun createPointer(): Pointer<StandardHtmlSymbolsScope> {
      val tag = SmartPointerManager.createPointer(this.tag)
      return Pointer {
        tag.dereference()?.let {
          StandardHtmlSymbolsScope(it)
        }
      }
    }

    override fun getSymbols(qualifiedKind: WebSymbolQualifiedKind,
                            params: WebSymbolsListSymbolsQueryParams,
                            scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
      if (params.queryExecutor.allowResolve) {
        when (qualifiedKind) {
          WebSymbol.HTML_ELEMENTS ->
            (getStandardHtmlElementDescriptor(tag)?.getElementsDescriptors(tag)
             ?: getHtmlNSDescriptor(tag.project)?.getAllElementsDescriptors(null)
             ?: emptyArray())
              .map { HtmlElementDescriptorBasedSymbol(it, tag) }
              .toList()
          WebSymbol.HTML_ATTRIBUTES ->
            getStandardHtmlAttributeDescriptors(tag)
              .map { HtmlAttributeDescriptorBasedSymbol(it, tag) }
              .toList()
          WebSymbol.JS_EVENTS ->
            getStandardHtmlAttributeDescriptors(tag)
              .filter { it.name.startsWith("on") }
              .map { HtmlEventDescriptorBasedSymbol(it) }
              .toList()
          else -> emptyList()
        }
      }
      else emptyList()

    override fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName,
                                    params: WebSymbolsNameMatchQueryParams,
                                    scope: Stack<WebSymbolsScope>): List<WebSymbol> {
      if (params.queryExecutor.allowResolve) {
        when (qualifiedName.qualifiedKind) {
          WebSymbol.HTML_ELEMENTS ->
            getStandardHtmlElementDescriptor(tag, qualifiedName.name)
              ?.let { HtmlElementDescriptorBasedSymbol(it, tag) }
              ?.match(qualifiedName.name, params, scope)
              ?.let { return it }
          WebSymbol.HTML_ATTRIBUTES ->
            getStandardHtmlAttributeDescriptor(tag, qualifiedName.name)
              ?.let { HtmlAttributeDescriptorBasedSymbol(it, tag) }
              ?.match(qualifiedName.name, params, scope)
              ?.let { return it }
          WebSymbol.JS_EVENTS -> {
            getStandardHtmlAttributeDescriptor(tag, "on${qualifiedName.name}")
              ?.let { HtmlEventDescriptorBasedSymbol(it) }
              ?.match(qualifiedName.name, params, scope)
              ?.let { return it }
          }
        }
      }
      return emptyList()
    }
  }

  abstract class StandardHtmlSymbol : MdnDocumentedSymbol(), PsiSourcedWebSymbol

  class HtmlElementDescriptorBasedSymbol(val descriptor: XmlElementDescriptor,
                                         private val tag: XmlTag?)
    : WebSymbol, StandardHtmlSymbol() {

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getHtmlMdnTagDocumentation(getHtmlApiNamespace(descriptor.nsDescriptor?.name, tag, name),
                                 name)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_HTML_ELEMENTS

    override val name: String = descriptor.name

    override val origin: WebSymbolOrigin
      get() = WebSymbolOrigin.empty()

    override val priority: WebSymbol.Priority
      get() = WebSymbol.Priority.LOW

    override val namespace: SymbolNamespace
      get() = WebSymbol.NAMESPACE_HTML

    override val defaultValue: String?
      get() = descriptor.defaultValue

    override val source: PsiElement?
      get() = descriptor.declaration

    override fun createPointer(): Pointer<HtmlElementDescriptorBasedSymbol> {
      val descriptor = this.descriptor
      val tagPtr = tag?.createSmartPointer()

      return Pointer<HtmlElementDescriptorBasedSymbol> {
        val tag = tagPtr?.let { it.dereference() ?: return@Pointer null }
        HtmlElementDescriptorBasedSymbol(descriptor, tag)
      }
    }

    override fun equals(other: Any?): Boolean =
      other === this ||
      other is HtmlElementDescriptorBasedSymbol
      && other.descriptor == this.descriptor

    override fun hashCode(): Int {
      return descriptor.hashCode()
    }
  }

  class HtmlAttributeDescriptorBasedSymbol private constructor(val descriptor: XmlAttributeDescriptor,
                                                               private val tag: XmlTag?,
                                                               private val tagName: String) : WebSymbol, StandardHtmlSymbol() {

    constructor(descriptor: XmlAttributeDescriptor, tag: XmlTag) : this(descriptor, tag, tag.name)

    constructor(descriptor: XmlAttributeDescriptor, tagName: String) : this(descriptor, null, tagName)

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getHtmlMdnAttributeDocumentation(getHtmlApiNamespace(tag?.namespace, tag, tagName),
                                       tagName, name)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_HTML_ATTRIBUTES

    override val name: String = descriptor.name

    override val origin: WebSymbolOrigin
      get() = WebSymbolOrigin.empty()

    override val priority: WebSymbol.Priority
      get() = WebSymbol.Priority.LOW

    override val namespace: SymbolNamespace
      get() = WebSymbol.NAMESPACE_HTML

    override val required: Boolean
      get() = descriptor.isRequired

    override val defaultValue: String?
      get() = descriptor.defaultValue

    override val source: PsiElement?
      get() = descriptor.declaration

    override val attributeValue: WebSymbolHtmlAttributeValue
      get() = WebSymbolHtmlAttributeValue.create(
        null,
        if (HtmlUtil.isBooleanAttribute(descriptor, null)) {
          WebSymbolHtmlAttributeValue.Type.BOOLEAN
        }
        else {
          WebSymbolHtmlAttributeValue.Type.STRING
        },
        true,
        descriptor.defaultValue,
        null
      )

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

    override fun hashCode(): Int =
      Objects.hash(descriptor, tag, tagName)

  }

  private class HtmlEventDescriptorBasedSymbol(descriptor: XmlAttributeDescriptor) : WebSymbol, StandardHtmlSymbol() {

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getDomEventDocumentation(name)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_JS_EVENTS

    override val name: String = descriptor.name.substring(2)

    override val origin: WebSymbolOrigin
      get() = WebSymbolOrigin.empty()

    override val priority: WebSymbol.Priority
      get() = WebSymbol.Priority.LOW

    override val namespace: SymbolNamespace
      get() = WebSymbol.NAMESPACE_JS

    override fun createPointer(): Pointer<HtmlEventDescriptorBasedSymbol> =
      Pointer.hardPointer(this)

  }
}

