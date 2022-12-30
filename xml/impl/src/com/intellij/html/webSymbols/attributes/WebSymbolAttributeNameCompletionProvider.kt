// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler
import com.intellij.html.webSymbols.WebSymbolsFrameworkHtmlSupport
import com.intellij.html.webSymbols.WebSymbolsHtmlAdditionalContextProvider
import com.intellij.html.webSymbols.WebSymbolsHtmlAdditionalContextProvider.Companion.getStandardHtmlAttributeDescriptors
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.WebSymbolsRegistry
import com.intellij.webSymbols.WebSymbolsRegistryManager
import com.intellij.webSymbols.codeInsight.AsteriskAwarePrefixMatcher
import com.intellij.webSymbols.codeInsight.WebSymbolsCompletionProviderBase

class WebSymbolAttributeNameCompletionProvider : WebSymbolsCompletionProviderBase<XmlElement>() {

  override fun getContext(position: PsiElement): XmlElement? =
    PsiTreeUtil.getParentOfType(position, XmlAttribute::class.java, HtmlTag::class.java)

  override fun addCompletions(parameters: CompletionParameters,
                              result: CompletionResultSet,
                              position: Int,
                              name: String,
                              registry: WebSymbolsRegistry,
                              context: XmlElement) {
    val tag = (context as? XmlAttribute)?.parent ?: context as XmlTag
    val patchedResultSet = result.withPrefixMatcher(
      AsteriskAwarePrefixMatcher(result.prefixMatcher.cloneWithPrefix(name)))

    val providedAttributes = tag.attributes.asSequence().mapNotNull { it.name }.toMutableSet()

    val attributesFilter = WebSymbolsFrameworkHtmlSupport.get(registry.framework)
      .getAttributeNameCodeCompletionFilter(tag)

    val symbols = (tag.descriptor as? WebSymbolElementDescriptor)?.symbol?.let { listOf(it) }
                  ?: registry.runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ELEMENTS, tag.name))

    val filteredOutStandardSymbols = getStandardHtmlAttributeDescriptors(tag)
      .map { it.name }.toMutableSet()

    processCompletionQueryResults(
      registry,
      patchedResultSet,
      NAMESPACE_HTML,
      KIND_HTML_ATTRIBUTES,
      name,
      position,
      symbols,
      providedAttributes,
      filter = { item ->
        if (item.symbol is WebSymbolsHtmlAdditionalContextProvider.StandardHtmlSymbol
            && item.offset == 0
            && item.symbol?.name == item.name) {
          filteredOutStandardSymbols.remove(item.name)
          false
        }
        else {
          item.offset <= name.length
          && attributesFilter.test(name.substring(0, item.offset) + item.name)
        }
      },
      consumer = { item ->
        item.withInsertHandlerAdded(
          { insertionContext, lookupItem ->
            // At this instant the file is already modified by LookupElement, so every PsiElement inside WebSymbolsRegistry is invalid
            // We need freshly constructed registry to avoid PsiInvalidElementAccessException when calling runNameMatchQuery
            val freshRegistry = WebSymbolsRegistryManager.get(context,
                                                              registry.allowResolve) // TODO Fix pointer dereference and use it here

            val fullName = name.substring(0, item.offset) + item.name
            val match = freshRegistry.runNameMatchQuery(listOf(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES, fullName), context = symbols)
            val info = WebSymbolHtmlAttributeInfo.create(fullName, freshRegistry, match)
            if (info != null && info.acceptsValue && !info.acceptsNoValue) {
              XmlAttributeInsertHandler.INSTANCE.handleInsert(insertionContext, lookupItem)
            }
          }
        ).addToResult(parameters, patchedResultSet)
      }
    )

    providedAttributes.addAll(filteredOutStandardSymbols)

    result.runRemainingContributors(parameters) { toPass ->
      val attrName = name.removeSuffix(toPass.prefixMatcher.prefix) + toPass.lookupElement.lookupString
      if (!providedAttributes.contains(attrName) && attributesFilter.test(attrName)) {
        val element = toPass.lookupElement
        result.withPrefixMatcher(AsteriskAwarePrefixMatcher(toPass.prefixMatcher))
          .withRelevanceSorter(toPass.sorter)
          .addElement(element)
      }
    }

  }


}