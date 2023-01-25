// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.ml.typos

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrection
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

internal class SearchEverywhereSpellingCorrectorImpl(private val project: Project) : SearchEverywhereSpellingCorrector {
  override fun isAvailableInTab(tabId: String): Boolean = tabId == ActionSearchEverywhereContributor::class.java.simpleName

  override fun suggestCorrectionFor(query: String): SearchEverywhereSpellingCorrection {
    if (query.isBlank()) return SearchEverywhereSpellingCorrection(null,0.0)

    return ActionsTabTypoFixSuggestionProvider(project)
             .suggestFixFor(query)
             ?.takeIf { isAboveMinimumConfidence(it) }
             ?.let { SearchEverywhereSpellingCorrection(it.suggestion, it.confidence) }
           ?: SearchEverywhereSpellingCorrection(null, 0.0)
  }

  private fun isAboveMinimumConfidence(suggestedCorrection: ActionsTabTypoFixSuggestionProvider.CorrectionResult): Boolean {
    return suggestedCorrection.confidence >= Registry.doubleValue("search.everywhere.ml.typos.min.confidence")
  }
}

internal class SearchEverywhereSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(project: Project): Boolean {
    return ApplicationManager.getApplication().isInternal
           && project.service<ActionsLanguageModel>().languageModel.isCompleted
  }

  override fun create(project: Project): SearchEverywhereSpellingCorrector = SearchEverywhereSpellingCorrectorImpl(project)
}
