// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.documentation.mdn.*
import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Companion.HTML_ATTRIBUTE_VALUES
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItemCustomizer
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.match
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.util.HtmlUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*

class WebSymbolsHtmlQueryConfigurator : WebSymbolsQueryConfigurator {

  override fun getScope(project: Project,
                        location: PsiElement?,
                        context: WebSymbolsContext,
                        allowResolve: Boolean): List<WebSymbolsScope> =
    if (location is XmlElement) {
      listOfNotNull(
        location.takeIf { it !is XmlTag }?.let { HtmlContextualWebSymbolsScope(it) },
        location.parentOfType<XmlTag>(withSelf = true)?.let { StandardHtmlSymbolsScope(it) },
      )
    }
    else emptyList()

  @ApiStatus.Internal
  class HtmlContextualWebSymbolsScope(private val location: PsiElement)
    : WebSymbolsCompoundScope(), WebSymbolsPrioritizedScope {

    init {
      assert(location !is XmlTag) { "Cannot create HtmlContextualWebSymbolsScope on a tag." }
    }

    override val priority: WebSymbol.Priority
      get() = WebSymbol.Priority.HIGHEST

    override fun requiresResolve(): Boolean = false

    override fun build(queryExecutor: WebSymbolsQueryExecutor, consumer: (WebSymbolsScope) -> Unit) {
      val context = location.parentOfTypes(XmlTag::class, XmlAttribute::class)
      val element = (context as? XmlTag) ?: (context as? XmlAttribute)?.parent ?: return
      val elementScope = element.takeIf { queryExecutor.allowResolve }
                           ?.descriptor?.asSafely<WebSymbolElementDescriptor>()?.symbol?.let { listOf(it) }
                         ?: queryExecutor.runNameMatchQuery(WebSymbol.NAMESPACE_HTML, WebSymbol.KIND_HTML_ELEMENTS, element.name)

      elementScope.forEach(consumer)

      val attribute = context as? XmlAttribute ?: return
      attribute.takeIf { queryExecutor.allowResolve }
        ?.descriptor?.asSafely<WebSymbolAttributeDescriptor>()?.symbol?.let(consumer)
      ?: queryExecutor.runNameMatchQuery(WebSymbol.NAMESPACE_HTML, WebSymbol.KIND_HTML_ATTRIBUTES,
                                         attribute.name, additionalScope = elementScope)
        .forEach(consumer)
    }

    override fun createPointer(): Pointer<out WebSymbolsCompoundScope> {
      val attributePtr = location.createSmartPointer()
      return Pointer {
        attributePtr.dereference()?.let { HtmlContextualWebSymbolsScope(location) }
      }
    }

    override fun equals(other: Any?): Boolean =
      other === this ||
      other is HtmlContextualWebSymbolsScope && other.location == location

    override fun hashCode(): Int =
      location.hashCode()
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
            (HtmlDescriptorUtils.getStandardHtmlElementDescriptor(tag)?.getElementsDescriptors(tag)
             ?: HtmlDescriptorUtils.getHtmlNSDescriptor(tag.project)?.getAllElementsDescriptors(null)
             ?: emptyArray())
              .map { HtmlElementDescriptorBasedSymbol(it, tag) }
              .toList()
          WebSymbol.HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors(tag)
              .map { HtmlAttributeDescriptorBasedSymbol(it, tag) }
              .toList()
          WebSymbol.JS_EVENTS ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors(tag)
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
            HtmlDescriptorUtils.getStandardHtmlElementDescriptor(tag, qualifiedName.name)
              ?.let { HtmlElementDescriptorBasedSymbol(it, tag) }
              ?.match(qualifiedName.name, params, scope)
              ?.let { return it }
          WebSymbol.HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptor(tag, qualifiedName.name)
              ?.let { HtmlAttributeDescriptorBasedSymbol(it, tag) }
              ?.match(qualifiedName.name, params, scope)
              ?.let { return it }
          WebSymbol.JS_EVENTS -> {
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptor(tag, "on${qualifiedName.name}")
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
      get() {
        val isBooleanAttribute = HtmlUtil.isBooleanAttribute(descriptor, null)
        return WebSymbolHtmlAttributeValue.create(
          null,
          when {
            isBooleanAttribute -> WebSymbolHtmlAttributeValue.Type.BOOLEAN
            descriptor.isEnumerated -> WebSymbolHtmlAttributeValue.Type.ENUM
            else -> WebSymbolHtmlAttributeValue.Type.STRING
          },
          !isBooleanAttribute,
          descriptor.defaultValue,
          null,
        )
      }

    override fun getSymbols(
      qualifiedKind: WebSymbolQualifiedKind,
      params: WebSymbolsListSymbolsQueryParams,
      scope: Stack<WebSymbolsScope>,
    ): List<WebSymbolsScope> =
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

  private class HtmlAttributeValueSymbol(override val name: @NlsSafe String) : WebSymbol {
    override val origin: WebSymbolOrigin
      get() = WebSymbolOrigin.empty()

    override val namespace: @NlsSafe SymbolNamespace
      get() = WebSymbol.NAMESPACE_HTML

    override val kind: @NlsSafe SymbolKind
      get() = WebSymbol.KIND_HTML_ATTRIBUTE_VALUES

    override fun createPointer(): Pointer<HtmlAttributeValueSymbol> =
      Pointer.hardPointer(this)

  }
}

