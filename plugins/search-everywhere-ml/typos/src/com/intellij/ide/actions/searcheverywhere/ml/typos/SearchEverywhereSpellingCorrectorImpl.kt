// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.ml.typos

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrection
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class SearchEverywhereSpellingCorrectorImpl(private val project: Project) : SearchEverywhereSpellingCorrector {
  override fun isAvailableInTab(tabId: String): Boolean = tabId == ActionSearchEverywhereContributor::class.java.simpleName

  override fun suggestCorrectionFor(query: String): SearchEverywhereSpellingCorrection {
    if (query.isBlank()) return SearchEverywhereSpellingCorrection(null)

    return ActionsTabTypoFixSuggestionProvider(project)
      .suggestFixFor(query)
      .let { SearchEverywhereSpellingCorrection(it) }
  }
}

internal class SearchEverywhereSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(project: Project): Boolean {
    return ApplicationManager.getApplication().isInternal
           && project.service<ActionsLanguageModel>().languageModel.isCompleted
  }

  override fun create(project: Project): SearchEverywhereSpellingCorrector = SearchEverywhereSpellingCorrectorImpl(project)
}
