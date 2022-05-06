package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.psi.PsiNamedElement

internal class SearchEverywhereClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    internal val IS_DEPRECATED = EventFields.Boolean("isDeprecated")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf<EventField<*>>(IS_DEPRECATED)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val item = SearchEverywherePsiElementFeaturesProvider.getPsiElement(element) ?: return emptyList()

    val data = arrayListOf<EventPair<*>>()
    ReadAction.run<Nothing> {
      (item as? PsiNamedElement)?.name?.let { elementName ->
        data.addAll(getNameMatchingFeatures(elementName, searchQuery))
      }
    }

    val presentation = (element as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.presentation
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
