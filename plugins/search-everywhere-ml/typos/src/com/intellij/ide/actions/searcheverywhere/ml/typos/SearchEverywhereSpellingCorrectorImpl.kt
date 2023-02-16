// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.ml.typos

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

private class SearchEverywhereSpellingCorrectorImpl(private val project: Project) : SearchEverywhereSpellingCorrector {
  override fun isAvailableInTab(tabId: String): Boolean = tabId == ActionSearchEverywhereContributor::class.java.simpleName

  override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
    if (query.isBlank()) return SearchEverywhereSpellCheckResult.NoCorrection

    return ActionsTabTypoFixSuggestionProvider(project)
             .suggestFixFor(query)
             .takeIf { it is SearchEverywhereSpellCheckResult.Correction && isAboveMinimumConfidence(it) }
           ?: SearchEverywhereSpellCheckResult.NoCorrection
  }

  private fun isAboveMinimumConfidence(suggestedCorrection: SearchEverywhereSpellCheckResult.Correction): Boolean {
    return suggestedCorrection.confidence >= Registry.doubleValue("search.everywhere.ml.typos.min.confidence")
  }
}

private class SearchEverywhereSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(project: Project): Boolean {
    return isTypoFixingEnabled && (ActionsLanguageModel.getInstance()?.isComputed ?: false)
  }

  override fun create(project: Project): SearchEverywhereSpellingCorrector = SearchEverywhereSpellingCorrectorImpl(project)
}
