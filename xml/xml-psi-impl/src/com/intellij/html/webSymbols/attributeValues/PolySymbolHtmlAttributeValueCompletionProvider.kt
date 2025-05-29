// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.attributeValues

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.html.webSymbols.attributes.WebSymbolAttributeDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.asSafely
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.completion.AsteriskAwarePrefixMatcher
import com.intellij.webSymbols.completion.PolySymbolsCompletionProviderBase
import com.intellij.webSymbols.html.PolySymbolHtmlAttributeValue.Type
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

class PolySymbolHtmlAttributeValueCompletionProvider : PolySymbolsCompletionProviderBase<XmlAttributeValue>() {
  override fun getContext(position: PsiElement): XmlAttributeValue? =
    PsiTreeUtil.getParentOfType(position, XmlAttributeValue::class.java, false)

  override fun addCompletions(
    parameters: CompletionParameters, result: CompletionResultSet, position: Int,
    name: String, queryExecutor: WebSymbolsQueryExecutor, context: XmlAttributeValue
  ) {
    val patchedResultSet = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name))

    val attribute = context.parent as? XmlAttribute ?: return
    val attributeDescriptor = attribute.descriptor.asSafely<WebSymbolAttributeDescriptor>() ?: return

    val type = attributeDescriptor.symbol.attributeValue?.type?.takeIf { it == Type.ENUM || it == Type.SYMBOL }
               ?: return

    val providedNames = mutableSetOf(attribute.name)
    if (type == Type.ENUM) {
      processCompletionQueryResults(queryExecutor, patchedResultSet, PolySymbol.HTML_ATTRIBUTE_VALUES, name,
                                    position, context, providedNames = providedNames) {
        if (!it.completeAfterInsert) {
          it.addToResult(parameters, patchedResultSet)
        }
      }
    }
    else {
      processCompletionQueryResults(queryExecutor, patchedResultSet, PolySymbol.HTML_ATTRIBUTE_VALUES, name,
                                    position, context, providedNames = providedNames) {
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