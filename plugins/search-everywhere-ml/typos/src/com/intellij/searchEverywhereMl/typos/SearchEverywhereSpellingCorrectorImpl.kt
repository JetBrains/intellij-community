// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.typos

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel

private class SearchEverywhereSpellingCorrectorImpl : SearchEverywhereSpellingCorrector {
  private val suggestionProvider = ActionsTabTypoFixSuggestionProvider()

  override fun isAvailableInTab(tabId: String): Boolean = tabId == ActionSearchEverywhereContributor::class.java.simpleName

  override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
    if (query.isBlank()) return SearchEverywhereSpellCheckResult.NoCorrection

    return suggestionProvider
             .suggestFixFor(query)
             .takeIf { it is SearchEverywhereSpellCheckResult.Correction && isAboveMinimumConfidence(it) }
           ?: SearchEverywhereSpellCheckResult.NoCorrection
  }

  private fun isAboveMinimumConfidence(suggestedCorrection: SearchEverywhereSpellCheckResult.Correction): Boolean {
    return suggestedCorrection.confidence >= Registry.doubleValue("search.everywhere.ml.typos.min.confidence")
  }

  init {
    service<ActionsLanguageModel>() // Access the service to start computing the language model
  }
}

private class SearchEverywhereSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(project: Project): Boolean {
    return isTypoFixingEnabled
  }

  override fun create(project: Project): SearchEverywhereSpellingCorrector = SearchEverywhereSpellingCorrectorImpl()
}
