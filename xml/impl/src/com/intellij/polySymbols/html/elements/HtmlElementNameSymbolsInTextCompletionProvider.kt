// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.HtmlCompletionContributor.canProvideHtmlElementInTextCompletion
import com.intellij.codeInsight.completion.HtmlCompletionContributor.patchResultSetForHtmlElementInTextCompletion
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlText
import com.intellij.util.asSafely
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.completion.PolySymbolsCompletionProviderBase
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

internal class HtmlElementNameSymbolsInTextCompletionProvider : PolySymbolsCompletionProviderBase<XmlElement>() {

  override fun getContext(position: PsiElement): XmlElement? =
    position.parent.asSafely<XmlElement>()
      ?.takeIf { it is XmlDocument || it is XmlText }

  override fun addCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    position: Int,
    name: String,
    queryExecutor: PolySymbolQueryExecutor,
    context: XmlElement
  ) {
    if (!canProvideHtmlElementInTextCompletion(parameters)) return

    val patchedResultSet = patchResultSetForHtmlElementInTextCompletion(
      result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name)), parameters)

    processCompletionQueryResults(queryExecutor, patchedResultSet, HTML_ELEMENTS,
                                  name, position, context,
                                  filter = HtmlElementSymbolsCompletionProvider.Companion::filterStandardHtmlSymbols) {
      it.withInsertHandlerAdded(XmlTagInsertHandler.INSTANCE)
        .withName("<" + it.name)
        .withDisplayName("<" + (it.displayName ?: it.name))
        .withAliasesReplaced(setOf(it.name))
        .addToResult(parameters, patchedResultSet)
    }
  }

}