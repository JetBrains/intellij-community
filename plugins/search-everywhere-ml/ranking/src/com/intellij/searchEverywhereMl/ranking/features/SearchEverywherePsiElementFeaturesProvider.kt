package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.LanguageUsageStatistics
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.util.PathUtil
import com.intellij.util.Time.DAY
import com.intellij.util.Time.WEEK

internal class SearchEverywherePsiElementFeaturesProvider : SearchEverywhereElementFeaturesProvider(
  FileSearchEverywhereContributor::class.java,
  RecentFilesSEContributor::class.java,
  ClassSearchEverywhereContributor::class.java,
  SymbolSearchEverywhereContributor::class.java,
) {
  companion object {
    @JvmStatic
    val IS_INVALID_DATA_KEY = EventFields.Boolean("isInvalid")

    private val SIMILARITY_SCORE = EventFields.Double("similarityScore")
    private val IS_PURE_SEMANTIC = EventFields.Boolean("isPureSemantic")

    private val LANGUAGE_DATA_KEY = EventFields.StringValidatedByCustomRule("language", LangCustomRuleValidator::class.java)
    private val LANGUAGE_USE_COUNT_DATA_KEY = EventFields.Int("langUseCount")
    private val LANGUAGE_IS_MOST_USED_DATA_KEY = EventFields.Boolean("langIsMostUsed")
    private val LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY = EventFields.Boolean("langIsInTop3MostUsed")
    private val LANGUAGE_USED_IN_LAST_DAY = EventFields.Boolean("langUsedInLastDay")
    private val LANGUAGE_USED_IN_LAST_WEEK = EventFields.Boolean("langUsedInLastWeek")
    private val LANGUAGE_USED_IN_LAST_MONTH = EventFields.Boolean("langUsedInLastMonth")
    private val LANGUAGE_NEVER_USED_DATA_KEY = EventFields.Boolean("langNeverUsed")
    private val LANGUAGE_IS_SAME_AS_OPENED_FILE = EventFields.Boolean("langSameAsOpenedFile")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    IS_INVALID_DATA_KEY, SIMILARITY_SCORE, IS_PURE_SEMANTIC,
    LANGUAGE_DATA_KEY, LANGUAGE_USE_COUNT_DATA_KEY, LANGUAGE_IS_MOST_USED_DATA_KEY,
    LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY, LANGUAGE_USED_IN_LAST_DAY, LANGUAGE_USED_IN_LAST_WEEK,
    LANGUAGE_USED_IN_LAST_MONTH, LANGUAGE_NEVER_USED_DATA_KEY, LANGUAGE_IS_SAME_AS_OPENED_FILE
  )

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    if (element is PsiItemWithSimilarity<*>) {
      return buildList {
        addAll(getElementFeatures(element.value, currentTime, searchQuery, elementPriority, cache))
        element.similarityScore?.let { add(SIMILARITY_SCORE.with(it)) }
        add(IS_PURE_SEMANTIC.with(element.isPureSemantic))
      }
    }
    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element) ?: return emptyList()
    return getLanguageFeatures(psiElement, cache) + getNameFeatures(element, searchQuery)
  }

  private fun getLanguageFeatures(element: PsiElement, cache: FeaturesProviderCache?): List<EventPair<*>> {
    if (cache == null) return emptyList()

    val elementLanguage = ReadAction.compute<Language, Nothing> { element.language }
    val stats = cache.usageSortedLanguageStatistics.getOrDefault(elementLanguage.id, LanguageUsageStatistics.NEVER_USED)

    val languageUsageIndex = cache.usageSortedLanguageStatistics
      .values
      .take(3)
      .indexOf(stats)

    val isMostUsed = languageUsageIndex == 0
    val isInTop3MostUsed = languageUsageIndex < 3

    val timeSinceLastUsage = System.currentTimeMillis() - stats.lastUsed

    val features = mutableListOf(
      LANGUAGE_DATA_KEY.with(elementLanguage.id),
      LANGUAGE_IS_MOST_USED_DATA_KEY.with(isMostUsed),
      LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY.with(isInTop3MostUsed),
      LANGUAGE_USED_IN_LAST_DAY.with(timeSinceLastUsage <= DAY),
      LANGUAGE_USED_IN_LAST_WEEK.with(timeSinceLastUsage <= WEEK),
      LANGUAGE_USED_IN_LAST_MONTH.with(timeSinceLastUsage <= WEEK * 4L),
      LANGUAGE_NEVER_USED_DATA_KEY.with(stats == LanguageUsageStatistics.NEVER_USED),
    )

    if (cache.currentlyOpenedFile != null) {
      val openedFileLanguage = LanguageUtil.getFileLanguage(cache.currentlyOpenedFile)
      features.add(LANGUAGE_IS_SAME_AS_OPENED_FILE.with(openedFileLanguage == elementLanguage))
    }

    return features
  }

  private fun getNameFeatures(element: Any, searchQuery: String): Collection<EventPair<*>> {
    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element)
    if (psiElement is PsiFileSystemItem) return getFileNameMatchingFeatures(psiElement, searchQuery)

    return getElementName(element)?.let {
      getNameMatchingFeatures(it, searchQuery)
    } ?: emptyList()
  }

  private fun getElementName(element: Any) = when (element) {
    is PsiItemWithPresentation -> element.presentation.presentableText
    is PsiNamedElement -> ReadAction.compute<String, Nothing> { element.name }
    else -> null
  }

  /**
   * File name-matching features are different from other PsiElement name features,
   * as they remove filename extensions from both the file found and the query.
   */
  private fun getFileNameMatchingFeatures(item: PsiFileSystemItem, searchQuery: String): Collection<EventPair<*>> {
    val nameOfItem = item.virtualFile.nameWithoutExtension
    // Remove the directory and the extension if they are present
    val fileNameFromQuery = FileUtil.getNameWithoutExtension(PathUtil.getFileName(searchQuery))
    return getNameMatchingFeatures(nameOfItem, fileNameFromQuery)
  }
}

object SearchEverywherePsiElementFeaturesProviderUtils {
  fun getPsiElement(element: Any): PsiElement? = when (element) {
    is PsiItemWithSimilarity<*> -> getPsiElement(element.value)
    is PsiItemWithPresentation -> element.item
    is PsiElement -> element
    else -> null
  }
}
