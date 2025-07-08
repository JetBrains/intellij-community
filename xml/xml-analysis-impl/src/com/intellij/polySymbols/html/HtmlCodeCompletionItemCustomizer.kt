// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemCustomizer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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