package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereClassFeaturesProvider.Fields.IS_DEPRECATED
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  object Fields {
    val IS_DEPRECATED = EventFields.Boolean("isDeprecated")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf<EventField<*>>(IS_DEPRECATED)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    if (element is PsiItemWithSimilarity<*>) {
      return getElementFeatures(element.value, currentTime, searchQuery, elementPriority, cache, correction)
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
