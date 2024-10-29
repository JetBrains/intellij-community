package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.LanguageUsageStatistics
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.searchEverywhereMl.TextEmbeddingProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.IS_INVALID_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_IS_MOST_USED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_IS_SAME_AS_OPENED_FILE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_NEVER_USED_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_USED_IN_LAST_DAY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_USED_IN_LAST_MONTH
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_USED_IN_LAST_WEEK
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywherePsiElementFeaturesProvider.Fields.LANGUAGE_USE_COUNT_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.utils.convertNameToNaturalLanguage
import com.intellij.util.PathUtil
import com.intellij.util.Time.DAY
import com.intellij.util.Time.WEEK

internal class SearchEverywherePsiElementFeaturesProvider : SearchEverywhereElementFeaturesProvider(
  FileSearchEverywhereContributor::class.java,
  RecentFilesSEContributor::class.java,
  ClassSearchEverywhereContributor::class.java,
  SymbolSearchEverywhereContributor::class.java,
) {
  object Fields {
    @JvmStatic
    val IS_INVALID_DATA_KEY = EventFields.Boolean("isInvalid")

    val LANGUAGE_DATA_KEY = EventFields.StringValidatedByCustomRule("language", LangCustomRuleValidator::class.java)
    val LANGUAGE_USE_COUNT_DATA_KEY = EventFields.Int("langUseCount")
    val LANGUAGE_IS_MOST_USED_DATA_KEY = EventFields.Boolean("langIsMostUsed")
    val LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY = EventFields.Boolean("langIsInTop3MostUsed")
    val LANGUAGE_USED_IN_LAST_DAY = EventFields.Boolean("langUsedInLastDay")
    val LANGUAGE_USED_IN_LAST_WEEK = EventFields.Boolean("langUsedInLastWeek")
    val LANGUAGE_USED_IN_LAST_MONTH = EventFields.Boolean("langUsedInLastMonth")
    val LANGUAGE_NEVER_USED_DATA_KEY = EventFields.Boolean("langNeverUsed")
    val LANGUAGE_IS_SAME_AS_OPENED_FILE = EventFields.Boolean("langSameAsOpenedFile")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    IS_INVALID_DATA_KEY, LANGUAGE_DATA_KEY, LANGUAGE_USE_COUNT_DATA_KEY, LANGUAGE_IS_MOST_USED_DATA_KEY,
    LANGUAGE_IS_IN_TOP_3_MOST_USED_DATA_KEY, LANGUAGE_USED_IN_LAST_DAY, LANGUAGE_USED_IN_LAST_WEEK,
    LANGUAGE_USED_IN_LAST_MONTH, LANGUAGE_NEVER_USED_DATA_KEY, LANGUAGE_IS_SAME_AS_OPENED_FILE
  )

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()
    var similarityScore: Double? = null

    val item = if (element is PsiItemWithSimilarity<*>) {
      result.add(IS_SEMANTIC_ONLY.with(element.isPureSemantic))
      element.similarityScore?.let { similarityScore = it }
      element.value
    }
    else {
      result.add(IS_SEMANTIC_ONLY.with(false))
      element
    }

    if (similarityScore != null) {
      result.add(SIMILARITY_SCORE.with(roundDouble(similarityScore)))
    }
    else if (ApplicationManager.getApplication().isEAP) { // for now, we can collect the data only from EAP builds
      val elementName = getElementName(item)
      val elementEmbedding = elementName?.let { TextEmbeddingProvider.getProvider()?.embed(convertNameToNaturalLanguage(it)) }
      val queryEmbedding = getQueryEmbedding(searchQuery, split = true)
      if (elementEmbedding != null && queryEmbedding != null) {
        result.add(SIMILARITY_SCORE.with(roundDouble(elementEmbedding.cosine(queryEmbedding).toDouble())))
      }
    }

    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(item) ?: return emptyList()
    result.addAll(getLanguageFeatures(psiElement, cache))
    result.addAll(getNameFeatures(item, searchQuery))
    return result
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
      is PsiItemWithPresentation -> element.item as? PsiNamedElement
      is PsiNamedElement -> element
      else -> null
    }?.let {
      ReadAction.compute<String, Nothing> { it.name }
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
