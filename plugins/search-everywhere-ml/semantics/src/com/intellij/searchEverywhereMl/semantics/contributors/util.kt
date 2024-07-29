package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

// equivalent to f1(inp) ?: ... ?: fn(inp)
@ApiStatus.Internal
fun <T, U> makeFirstYieldingNotNullOrNull(vararg fs: (T) -> U?): (T) -> U? =
  { inp -> fs.firstNotNullOfOrNull { f -> f(inp) } }

@ApiStatus.Internal
fun tryPsiElementFromPossiblySemanticEntry(entry: Any): PsiElement? =
  when (entry) {
    is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> entry.item
    is PSIPresentationBgRendererWrapper.ItemWithPresentation<*> -> when (val presUnwrapped = entry.item) {
      is PsiItemWithSimilarity<*> -> when (val semUnwrapped = presUnwrapped.value) {
        is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> semUnwrapped.item
        is PsiElement -> semUnwrapped
        else -> null
      }
      else -> null
    }
    else -> null
  }