// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols.elements

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LegacyCompletionContributor
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.completion.WebSymbolsCompletionProviderBase

class WebSymbolElementNameCompletionProvider : WebSymbolsCompletionProviderBase<HtmlTag>() {

  override fun getContext(position: PsiElement): HtmlTag? =
    PsiTreeUtil.getParentOfType(position, HtmlTag::class.java, false)

  override fun addCompletions(parameters: CompletionParameters,
                              result: CompletionResultSet,
                              position: Int,
                              name: String,
                              queryExecutor: WebSymbolsQueryExecutor,
                              context: HtmlTag) {
    var endTag = false
    LegacyCompletionContributor.processReferences(parameters, result) { reference, _ ->
      if (reference is TagNameReference && !reference.isStartTagFlag) endTag = true
    }
    if (endTag) return

    val patchedResultSet = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name))
    processCompletionQueryResults(queryExecutor, patchedResultSet, NAMESPACE_HTML, KIND_HTML_ELEMENTS, name,
                                  position, filter = Companion::filterStandardHtmlSymbols) {
      it.withInsertHandlerAdded(XmlTagInsertHandler.INSTANCE)
        .addToResult(parameters, patchedResultSet)
    }
  }

  companion object {

    fun filterStandardHtmlSymbols(item: WebSymbolCodeCompletionItem) =
      item.symbol !is WebSymbolsHtmlQueryConfigurator.StandardHtmlSymbol
      || item.offset != 0
      || item.symbol?.name != item.name

  }

}