// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributes

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler
import com.intellij.html.webSymbols.HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors
import com.intellij.html.webSymbols.WebSymbolsFrameworkHtmlSupport
import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.webSymbols.WebSymbol.Companion.HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.completion.AsteriskAwarePrefixMatcher
import com.intellij.webSymbols.completion.WebSymbolsCompletionProviderBase
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.utils.asSingleSymbol

class WebSymbolAttributeNameCompletionProvider : WebSymbolsCompletionProviderBase<XmlElement>() {

  override fun getContext(position: PsiElement): XmlElement? =
    PsiTreeUtil.getParentOfType(position, XmlAttribute::class.java, HtmlTag::class.java)

  override fun addCompletions(parameters: CompletionParameters,
                              result: CompletionResultSet,
                              position: Int,
                              name: String,
                              queryExecutor: WebSymbolsQueryExecutor,
                              context: XmlElement) {
    val tag = (context as? XmlAttribute)?.parent ?: context as XmlTag
    val patchedResultSet = result.withPrefixMatcher(
      AsteriskAwarePrefixMatcher(result.prefixMatcher.cloneWithPrefix(name)))

    val providedAttributes = tag.attributes.asSequence().mapNotNull { it.name }.toMutableSet()

    val attributesFilter = WebSymbolsFrameworkHtmlSupport.get(queryExecutor.framework)
      .getAttributeNameCodeCompletionFilter(tag)

    val symbols = (tag.descriptor as? WebSymbolElementDescriptor)?.symbol?.let { listOf(it) }
                  ?: queryExecutor.runNameMatchQuery(NAMESPACE_HTML, KIND_HTML_ELEMENTS, tag.name)

    val filteredOutStandardSymbols = getStandardHtmlAttributeDescriptors(tag)
      .map { it.name }.toMutableSet()

    processCompletionQueryResults(
      queryExecutor,
      patchedResultSet,
      HTML_ATTRIBUTES,
      name,
      position,
      context,
      symbols,
      providedAttributes,
      filter = { item ->
        if (item.symbol is WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol
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
            val freshRegistry = WebSymbolsQueryExecutorFactory.create(context,
                                                                      queryExecutor.allowResolve) // TODO Fix pointer dereference and use it here

            val fullName = name.substring(0, item.offset) + item.name
            val match = freshRegistry.runNameMatchQuery(NAMESPACE_HTML, KIND_HTML_ATTRIBUTES, fullName, scope = symbols)
                          .asSingleSymbol() ?: return@withInsertHandlerAdded
            val info = WebSymbolHtmlAttributeInfo.create(fullName, freshRegistry, match)
            if (info.acceptsValue && !info.acceptsNoValue) {
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