// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService
import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Provides model to predict relevance of each element in Search Everywhere tab
 */
internal abstract class SearchEverywhereMLRankingModelProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<SearchEverywhereMLRankingModelProvider>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.rankingModelProvider")

    fun getForTab(contributorId: String): SearchEverywhereMLRankingModelProvider {
      return EP_NAME.findFirstSafe {
        it.supportedContributor.simpleName == contributorId
      } ?: throw IllegalArgumentException("Unsupported contributor $contributorId")
    }
  }

  abstract val model: DecisionFunction

  protected abstract val supportedContributor: Class<out SearchEverywhereContributor<*>>

  protected fun shouldProvideExperimentalModel(): Boolean {
    return SearchEverywhereMlSessionService.getService().shouldUseExperimentalModel(supportedContributor.simpleName)
  }
}