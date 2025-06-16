// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols

import com.intellij.documentation.mdn.*
import com.intellij.html.polySymbols.attributes.HtmlAttributeSymbolDescriptor
import com.intellij.html.polySymbols.elements.HtmlElementSymbolDescriptor
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemCustomizer
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.html.*
import com.intellij.polySymbols.js.JS_EVENTS
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolPrioritizedScope
import com.intellij.polySymbols.utils.match
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.util.HtmlUtil
import org.jetbrains.annotations.ApiStatus

class HtmlSymbolQueryConfigurator : PolySymbolQueryConfigurator {

  override fun getScope(
    project: Project,
    location: PsiElement?,
    context: PolyContext,
    allowResolve: Boolean,
  ): List<PolySymbolScope> =
    if (location is XmlElement) {
      listOfNotNull(
        location.takeIf { it !is XmlTag }?.let { HtmlContextualSymbolScope(it) },
        location.parentOfType<XmlTag>(withSelf = true)?.let { StandardHtmlSymbolScope(it) },
      )
    }
    else emptyList()

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

  class HtmlCodeCompletionItemCustomizer : PolySymbolCodeCompletionItemCustomizer {
    override fun customize(
      item: PolySymbolCodeCompletionItem,
      framework: FrameworkId?,
      qualifiedKind: PolySymbolQualifiedKind,
      location: PsiElement,
    ): PolySymbolCodeCompletionItem =
      when (qualifiedKind) {
        HTML_ELEMENTS -> item.withTypeText(item.symbol?.origin?.library)
        HTML_ATTRIBUTES -> item // TODO - we can figure out the actual type with full match provided
        else -> item
      }
  }

  private class StandardHtmlSymbolScope(private val tag: XmlTag) : PolySymbolScope {

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
              .map { HtmlElementDescriptorBasedSymbol(it, tag) }
              .toList()
          HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors(tag)
              .map { HtmlAttributeDescriptorBasedSymbol(it, tag) }
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
              ?.let { HtmlElementDescriptorBasedSymbol(it, tag) }
              ?.match(qualifiedName.name, params, stack)
              ?.let { return it }
          HTML_ATTRIBUTES ->
            HtmlDescriptorUtils.getStandardHtmlAttributeDescriptor(tag, qualifiedName.name)
              ?.let { HtmlAttributeDescriptorBasedSymbol(it, tag) }
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

  abstract class StandardHtmlSymbol : MdnDocumentedSymbol(), PsiSourcedPolySymbol, PolySymbolScope {
    abstract val project: Project?
    override fun getModificationCount(): Long = project?.let { PsiModificationTracker.getInstance(it).modificationCount } ?: 0
    abstract override fun createPointer(): Pointer<out StandardHtmlSymbol>
  }

  class HtmlElementDescriptorBasedSymbol(
    val descriptor: XmlElementDescriptor,
    private val tag: XmlTag?,
  ) : PolySymbol, StandardHtmlSymbol() {

    override val project: Project?
      get() = tag?.project ?: descriptor.declaration?.project

    override fun getMdnDocumentation(): MdnSymbolDocumentation? =
      getHtmlMdnTagDocumentation(getHtmlApiNamespace(descriptor.nsDescriptor?.name, tag, name),
                                 name)

    override val qualifiedKind: PolySymbolQualifiedKind
      get() = HTML_ELEMENTS

    override val name: String = descriptor.name

    override val origin: PolySymbolOrigin
      get() = PolySymbolOrigin.empty()

    override val priority: PolySymbol.Priority
      get() = PolySymbol.Priority.LOW

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

    override fun hashCode(): Int =
      descriptor.hashCode()
  }

  class HtmlAttributeDescriptorBasedSymbol private constructor(
    val descriptor: XmlAttributeDescriptor,
    private val tag: XmlTag?,
    private val tagName: String,
  ) : StandardHtmlSymbol() {

    constructor(descriptor: XmlAttributeDescriptor, tag: XmlTag) : this(descriptor, tag, tag.name)

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
      get() = PolySymbolOrigin.empty()

    override val priority: PolySymbol.Priority
      get() = PolySymbol.Priority.LOW

    override val modifiers: Set<PolySymbolModifier>
      get() = setOf(
        if (descriptor.isRequired) PolySymbolModifier.REQUIRED else PolySymbolModifier.OPTIONAL,
      )

    override val defaultValue: String?
      get() = descriptor.defaultValue

    override val source: PsiElement?
      get() = descriptor.declaration

    val attributeValue: PolySymbolHtmlAttributeValue
      get() {
        val isBooleanAttribute = HtmlUtil.isBooleanAttribute(descriptor, null)
        return PolySymbolHtmlAttributeValue.create(
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

  private class HtmlAttributeValueSymbol(override val name: @NlsSafe String) : PolySymbol {
    override val origin: PolySymbolOrigin
      get() = PolySymbolOrigin.empty()

    override val qualifiedKind: PolySymbolQualifiedKind
      get() = HTML_ATTRIBUTE_VALUES

    override fun createPointer(): Pointer<HtmlAttributeValueSymbol> =
      Pointer.hardPointer(this)

  }
}

