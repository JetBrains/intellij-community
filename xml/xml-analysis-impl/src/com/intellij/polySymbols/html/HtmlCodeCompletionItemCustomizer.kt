// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemCustomizer
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HtmlCodeCompletionItemCustomizer : PolySymbolCodeCompletionItemCustomizer {
  override fun customize(
    item: PolySymbolCodeCompletionItem,
    context: PolyContext,
    kind: PolySymbolKind,
    location: PsiElement,
  ): PolySymbolCodeCompletionItem =
    when (kind) {
      HTML_ELEMENTS -> item.withTypeText {
        item.symbol
          ?.getDocumentationTarget(null)
          ?.asSafely<PolySymbolDocumentationTarget>()
          ?.documentation
          ?.library
          ?.let {
            val atIndex = it.lastIndexOf('@')
            if (atIndex > 0) it.substring(0, atIndex) else it
          }
      }
      HTML_ATTRIBUTES -> item // TODO - we can figure out the actual type with full match provided
      else -> item
    }
}