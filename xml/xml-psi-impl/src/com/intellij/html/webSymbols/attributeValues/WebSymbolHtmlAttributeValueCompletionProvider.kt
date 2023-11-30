// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributeValues

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.completion.AsteriskAwarePrefixMatcher
import com.intellij.webSymbols.completion.WebSymbolsCompletionProviderBase
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue.Type
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class WebSymbolHtmlAttributeValueCompletionProvider : WebSymbolsCompletionProviderBase<XmlAttribute>() {
  override fun getContext(position: PsiElement): XmlAttribute? =
    PsiTreeUtil.getParentOfType(position, XmlAttribute::class.java, false)

  override fun addCompletions(parameters: CompletionParameters, result: CompletionResultSet, position: Int,
                              name: String, queryExecutor: WebSymbolsQueryExecutor, context: XmlAttribute) {
    val patchedResultSet = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name))

    val attributeDescriptor = context.descriptor.asSafely<WebSymbolAttributeDescriptor>() ?: return

    val queryScope = getHtmlAttributeValueQueryScope(queryExecutor, context)
                     ?: return

    val type = attributeDescriptor.symbol.attributeValue?.type?.takeIf { it == Type.ENUM || it == Type.SYMBOL }
               ?: return

    val providedNames = mutableSetOf(context.name)
    if (type == Type.ENUM) {
      processCompletionQueryResults(queryExecutor, patchedResultSet, WebSymbol.HTML_ATTRIBUTE_VALUES, name,
                                    position, context, queryScope, providedNames) {
        if (!it.completeAfterInsert) {
          it.addToResult(parameters, patchedResultSet)
        }
      }
    }
    else {
      processCompletionQueryResults(queryExecutor, patchedResultSet, WebSymbol.HTML_ATTRIBUTE_VALUES, name,
                                    position, context, queryScope, providedNames) {
        it.addToResult(parameters, patchedResultSet)
      }
    }

    result.runRemainingContributors(parameters) { toPass ->
      if (!providedNames.contains(toPass.lookupElement.lookupString)) {
        val element = toPass.lookupElement
        result.withPrefixMatcher(AsteriskAwarePrefixMatcher(toPass.prefixMatcher))
          .withRelevanceSorter(toPass.sorter)
          .addElement(element)
      }
    }
  }
}