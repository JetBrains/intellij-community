package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.psi.PsiElement
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity


fun extractPsiElement(element: PsiItemWithSimilarity<*>): PsiElement? {
  return when (val value = element.value) {
    is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> value.item
    is PsiElement -> value
    else -> null
  }
}
