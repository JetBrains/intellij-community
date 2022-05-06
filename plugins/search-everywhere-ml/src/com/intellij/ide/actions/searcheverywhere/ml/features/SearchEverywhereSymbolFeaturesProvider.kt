package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatisticianService
import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

internal class SearchEverywhereSymbolFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(SymbolSearchEverywhereContributor::class.java) {
  companion object {
    private val LANGUAGE_DATA_KEY = EventFields.StringValidatedByCustomRule("language", LangCustomRuleValidator::class.java)
    private val PARENT_STAT_USE_COUNT_DATA_KEY = EventFields.Int("parentStatUseCount")
    private val PARENT_STAT_IS_MOST_POPULAR_DATA_KEY = EventFields.Boolean("parentStatIsMostPopular")
    private val PARENT_STAT_RECENCY_DATA_KEY = EventFields.Int("parentStatRecency")
    private val PARENT_STAT_IS_MOST_RECENT_DATA_KEY = EventFields.Boolean("parentStatIsMostRecent")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(LANGUAGE_DATA_KEY, PARENT_STAT_USE_COUNT_DATA_KEY, PARENT_STAT_IS_MOST_POPULAR_DATA_KEY,
                  PARENT_STAT_RECENCY_DATA_KEY, PARENT_STAT_IS_MOST_RECENT_DATA_KEY)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val psiElement = getPsiElement(element) ?: return emptyList()
    val data = arrayListOf<EventPair<*>>()

    data.add(LANGUAGE_DATA_KEY.with(getPsiElementLanguage(psiElement)))
    data.addAll(getParentStatisticianFeatures(psiElement))

    getElementName(element)?.let { name ->
      data.addAll(getNameMatchingFeatures(name, searchQuery))
    }

    return data
  }

  private fun getPsiElement(element: Any) = when (element) {
    is PsiItemWithPresentation -> element.first
    is PsiElement -> element
    else -> null
  }

  private fun getPsiElementLanguage(element: PsiElement) = ReadAction.compute<String, Nothing> { element.language.id }

  private fun getParentStatisticianFeatures(element: PsiElement): List<EventPair<*>> {
    val parent = ReadAction.compute<PsiElement?, Nothing> { element.parent }?.takeIf { it is PsiNamedElement } ?: return emptyList()
    val service = service<SearchEverywhereStatisticianService>()

    return service.getCombinedStats(parent)?.let { stats ->
      arrayListOf(
        PARENT_STAT_USE_COUNT_DATA_KEY.with(stats.useCount),
        PARENT_STAT_IS_MOST_POPULAR_DATA_KEY.with(stats.isMostPopular),
        PARENT_STAT_RECENCY_DATA_KEY.with(stats.recency),
        PARENT_STAT_IS_MOST_RECENT_DATA_KEY.with(stats.isMostRecent),
      )
    } ?: emptyList()
  }

  private fun getElementName(element: Any): String? {
    if (element is PsiItemWithPresentation) return element.presentation.presentableText
    if (element !is PsiNamedElement) return null

    return ReadAction.compute<String?, Nothing> { element.name }
  }
}
