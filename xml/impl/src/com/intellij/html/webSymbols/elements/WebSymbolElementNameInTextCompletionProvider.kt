// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

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
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.completion.WebSymbolsCompletionProviderBase

class WebSymbolElementNameInTextCompletionProvider : WebSymbolsCompletionProviderBase<XmlElement>() {

  override fun getContext(position: PsiElement): XmlElement? =
    position.parent.asSafely<XmlElement>()
      ?.takeIf { it is XmlDocument || it is XmlText }

  override fun addCompletions(parameters: CompletionParameters,
                              result: CompletionResultSet,
                              position: Int,
                              name: String,
                              queryExecutor: WebSymbolsQueryExecutor,
                              context: XmlElement) {
    if (!canProvideHtmlElementInTextCompletion(parameters)) return

    val patchedResultSet = patchResultSetForHtmlElementInTextCompletion(
      result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name)), parameters)

    processCompletionQueryResults(queryExecutor, patchedResultSet, NAMESPACE_HTML, KIND_HTML_ELEMENTS, name, position,
                                  filter = WebSymbolElementNameCompletionProvider.Companion::filterStandardHtmlSymbols) {
      it.withInsertHandlerAdded(XmlTagInsertHandler.INSTANCE)
        .withName("<" + it.name)
        .withDisplayName("<" + (it.displayName ?: it.name))
        .withAliasesReplaced(setOf(it.name))
        .addToResult(parameters, patchedResultSet)
    }
  }

}