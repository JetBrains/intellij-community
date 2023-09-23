package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.psi.PsiElement
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity

const val SHOW_PROGRESS_REGISTRY_KEY = "search.everywhere.ml.semantic.indexing.show.progress"

fun extractPsiElement(element: PsiItemWithSimilarity<*>): PsiElement? {
  return when (val value = element.value) {
    is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> value.item
    is PsiElement -> value
    else -> null
  }
}
