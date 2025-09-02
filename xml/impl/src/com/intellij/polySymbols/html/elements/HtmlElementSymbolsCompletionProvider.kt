// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.elements

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LegacyCompletionContributor
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.polySymbols.html.StandardHtmlSymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolsCompletionProviderBase
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.util.PsiTreeUtil

class HtmlElementSymbolsCompletionProvider : PolySymbolsCompletionProviderBase<HtmlTag>() {

  override fun getContext(position: PsiElement): HtmlTag? =
    PsiTreeUtil.getParentOfType(position, HtmlTag::class.java, false)

  override fun addCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    position: Int,
    name: String,
    queryExecutor: PolySymbolQueryExecutor,
    context: HtmlTag,
  ) {
    var endTag = false
    LegacyCompletionContributor.processReferences(parameters, result) { reference, _ ->
      if (reference is TagNameReference && !reference.isStartTagFlag) endTag = true
    }
    if (endTag) return

    val patchedResultSet = result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(name))
    processCompletionQueryResults(queryExecutor, patchedResultSet, HTML_ELEMENTS, name,
                                  position, context, filter = Companion::filterStandardHtmlSymbols) {
      it.withInsertHandlerAdded(XmlTagInsertHandler.INSTANCE)
        .addToResult(parameters, patchedResultSet)
    }
  }

  companion object {

    fun filterStandardHtmlSymbols(item: PolySymbolCodeCompletionItem): Boolean =
      item.symbol !is StandardHtmlSymbol
      || item.offset != 0
      || item.symbol?.name != item.name

  }

}