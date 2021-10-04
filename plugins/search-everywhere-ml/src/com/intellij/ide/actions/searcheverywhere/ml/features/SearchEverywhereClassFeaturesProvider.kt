package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.psi.PsiElement

class SearchEverywhereClassFeaturesProvider : SearchEverywhereClassOrFileFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private const val PRIORITY = "priority"
    private const val IS_DEPRECATED = "isDeprecated"
  }

  override fun getElementFeatures(element: PsiElement,
                                  presentation: TargetPresentation?,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int): Map<String, Any> {
    val data = hashMapOf<String, Any>(
      PRIORITY to elementPriority,
    )
    data.putIfValueNotNull(IS_DEPRECATED, isDeprecated(presentation))
    return data
  }

  private fun isDeprecated(presentation: TargetPresentation?): Boolean? {
    if (presentation == null) {
      return null
    }

    val effectType = presentation.presentableTextAttributes?.effectType ?: return false
    return effectType == EffectType.STRIKEOUT
  }
}
