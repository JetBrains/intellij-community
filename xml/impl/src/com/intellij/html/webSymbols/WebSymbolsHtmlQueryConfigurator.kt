// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.documentation.mdn.*
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.asSafely
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItemCustomizer
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
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
                        element: PsiElement?,
                        context: WebSymbolsContext,
                        allowResolve: Boolean): List<WebSymbolsScope> =
    ((element as? XmlAttribute)?.parent ?: element as? XmlTag)?.let {
      listOf(
        HtmlElementSymbolsScope(it.project),
        HtmlElementAttributesAndEventsScope(it)
      )
    }
    ?: (element as? XmlElement)?.let {
      listOf(HtmlElementSymbolsScope(it.project))
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
        ?.getDefaultAttributeDescriptor(attrName, tag)
        ?.takeIf { !it.getName(tag).contains(':') }

    private fun getHtmlElementDescriptor(tag: XmlTag): HtmlElementDescriptorImpl? =
      when (val tagDescriptor = tag.descriptor) {
        is HtmlElementDescriptorImpl -> tagDescriptor
        is WebSymbolElementDescriptor, is AnyXmlElementDescriptor -> {
          tag.getNSDescriptor(tag.namespace, false)
            .asSafely<HtmlNSDescriptorImpl>()
            ?.let { nsDescriptor ->
              nsDescriptor.getElementDescriptorByName(tag.localName)
              ?: nsDescriptor.getElementDescriptorByName("div")
              ?: nsDescriptor.getElementDescriptorByName("span")
            } as? HtmlElementDescriptorImpl
        }
        else -> null
      }

    fun Sequence<WebSymbolCodeCompletionItem>.filterOutStandardHtmlSymbols(): Sequence<WebSymbolCodeCompletionItem> =
      filter {
        it.symbol !is StandardHtmlSymbol
        || it.offset > 0
        || it.symbol?.name != it.name
      }

    fun List<WebSymbol>.hasOnlyStandardHtmlSymbols(): Boolean =
      flatMap { it.unwrapMatchedSymbols() }
        .all { it is StandardHtmlSymbol }

    fun WebSymbol.hasOnlyStandardHtmlSymbolsOrExtensions(): Boolean =
      unwrapMatchedSymbols()
        .all { it is StandardHtmlSymbol || it.extension }

  }

  class HtmlSymbolsCodeCompletionItemCustomizer : WebSymbolCodeCompletionItemCustomizer {
    override fun customize(item: WebSymbolCodeCompletionItem,
                           framework: FrameworkId?, namespace: SymbolNamespace, kind: SymbolKind): WebSymbolCodeCompletionItem =
      item.let {
        if (namespace == WebSymbol.NAMESPACE_HTML)
          when (kind) {
            WebSymbol.KIND_HTML_ELEMENTS -> it.withTypeText(it.symbol?.origin?.library)
            WebSymbol.KIND_HTML_ATTRIBUTES -> it // TODO - we can figure out the actual type with full match provided
            else -> it
          }
        else it
      }
  }

  private class HtmlElementSymbolsScope(project: Project)
    : WebSymbolsScopeWithCache<Project, Unit>(null, project, project, Unit) {

    override fun provides(namespace: SymbolNamespace, kind: SymbolKind): Boolean =
      namespace == WebSymbol.NAMESPACE_HTML && kind == WebSymbol.KIND_HTML_ELEMENTS

    override fun getModificationCount(): Long = 0

    override fun createPointer(): Pointer<HtmlElementSymbolsScope> =
      Pointer.hardPointer(this)

    override fun initialize(consumer: (WebSymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      val descriptor = getHtmlNSDescriptor(project) ?: return
      descriptor.getAllElementsDescriptors(null).forEach {
        consumer(HtmlElementDescriptorBasedSymbol(it, null))
      }
      descriptor.descriptorFile?.let { cacheDependencies.add(it) }
    }
  }

  private class HtmlElementAttributesAndEventsScope(private val tag: XmlTag) : WebSymbolsScope {

    override fun equals(other: Any?): Boolean =
      other is HtmlElementAttributesAndEventsScope
      && other.tag == tag

    override fun hashCode(): Int = tag.hashCode()

    override fun getModificationCount(): Long = 0

    override fun createPointer(): Pointer<HtmlElementAttributesAndEventsScope> {
      val tag = SmartPointerManager.createPointer(this.tag)
      return Pointer {
        tag.dereference()?.let {
          HtmlElementAttributesAndEventsScope(it)
        }
      }
    }

    override fun getSymbols(namespace: SymbolNamespace?,
                            kind: String,
                            name: String?,
                            params: WebSymbolsNameMatchQueryParams,
                            scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
      if (params.queryExecutor.allowResolve) {
        if (namespace == null || namespace == WebSymbol.NAMESPACE_HTML) {
          when (kind) {
            WebSymbol.KIND_HTML_ATTRIBUTES ->
              if (name.isNullOrEmpty()) {
                getStandardHtmlAttributeDescriptors(tag)
                  .map { HtmlAttributeDescriptorBasedSymbol(it, tag) }
                  .toList()
              }
              else {
                getStandardHtmlAttributeDescriptor(tag, name)
                  ?.let { HtmlAttributeDescriptorBasedSymbol(it, tag) }
                  ?.match(name, scope, params)
                ?: emptyList()
              }
            else -> emptyList()
          }
        }
        else if (namespace == WebSymbol.NAMESPACE_JS && kind == WebSymbol.KIND_JS_EVENTS) {
          if (name.isNullOrEmpty()) {
            getStandardHtmlAttributeDescriptors(tag)
              .filter { it.name.startsWith("on") }
              .map { HtmlEventDescriptorBasedSymbol(it) }
              .toList()
          }
          else {
            getStandardHtmlAttributeDescriptor(tag, "on$name")
              ?.let { HtmlEventDescriptorBasedSymbol(it) }
              ?.match(name, scope, params)
            ?: emptyList()
          }
        }
        else emptyList()
      }
      else emptyList()
  }

  abstract class StandardHtmlSymbol : MdnDocumentedSymbol(), PsiSourcedWebSymbol

  class HtmlElementDescriptorBasedSymbol(val descriptor: XmlElementDescriptor,
                                         private val tag: XmlTag?)
    : WebSymbol, StandardHtmlSymbol() {

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getHtmlMdnTagDocumentation(getHtmlApiNamespace(descriptor.nsDescriptor?.name, tag, matchedName),
                                 matchedName)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_HTML_ELEMENTS

    override val matchedName: String = descriptor.name

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
                                       tagName, matchedName)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_HTML_ATTRIBUTES

    override val matchedName: String = descriptor.name

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
      getDomEventDocumentation(matchedName)

    override val kind: SymbolKind
      get() = WebSymbol.KIND_JS_EVENTS

    override val matchedName: String = descriptor.name.substring(2)

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