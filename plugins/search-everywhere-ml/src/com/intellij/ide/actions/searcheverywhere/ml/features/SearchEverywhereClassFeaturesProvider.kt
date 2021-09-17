package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.psi.PsiElement

class SearchEverywhereClassFeaturesProvider : SearchEverywhereClassOrFileFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private const val PRIORITY = "priority"
  }

  override fun getElementFeatures(element: PsiElement, currentTime: Long, searchQuery: String, elementPriority: Int): Map<String, Any> {
    return hashMapOf<String, Any>(
      PRIORITY to elementPriority
    )
  }
}
