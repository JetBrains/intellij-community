package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.backend.presentation.TargetPresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
class SearchEverywhereClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    val IS_DEPRECATED = EventFields.Boolean("isDeprecated")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf<EventField<*>>(IS_DEPRECATED)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    if (element is PsiItemWithSimilarity<*>) {
      return getElementFeatures(element.value, currentTime, searchQuery, elementPriority, cache)
    }
    val presentation = (element as? PSIPresentationBgRendererWrapper.PsiItemWithPresentation)?.presentation
    val isDeprecated = isDeprecated(presentation) ?: return emptyList()

    return listOf(IS_DEPRECATED.with(isDeprecated))
  }

  private fun isDeprecated(presentation: TargetPresentation?): Boolean? {
    if (presentation == null) {
      return null
    }

    val effectType = presentation.presentableTextAttributes?.effectType ?: return false
    return effectType == EffectType.STRIKEOUT
  }
}
