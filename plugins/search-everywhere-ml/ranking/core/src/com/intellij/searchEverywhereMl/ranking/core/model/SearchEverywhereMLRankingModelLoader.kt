// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.model.local.LocalRankingModelProviderUtil

/**
 * Loads ML model from module dependency or local file, loaded models predict relevance of each element in Search Everywhere tab
 */
internal abstract class SearchEverywhereMLRankingModelLoader {
  companion object {
    private val EP_NAME: ExtensionPointName<SearchEverywhereMLRankingModelLoader>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.rankingModelLoader")

    val allLoaders: List<SearchEverywhereMLRankingModelLoader>
      get() = EP_NAME.extensionList

    fun getForTab(tab: SearchEverywhereTab.TabWithMlRanking): SearchEverywhereMLRankingModelLoader {
      return EP_NAME.findFirstSafe {
        it.supportedTab == tab
      } ?: throw IllegalArgumentException("Unsupported tab identifier ${tab.tabId}")
    }
  }

  /**
   * Returns a model used for ranking.
   * If a path to a local model is specified, a local model will be returned, provided that the user is in an experimental group.
   * This is so that, a new model can be easily compared to an existing one.
   *
   * If no path is specified, then a bundled model will be provided which can either be experimental or standard,
   * depending on the return value of [useExperimentalModel].
   */
  fun loadModel(): DecisionFunction {
    if (shouldProvideLocalModel() && useExperimentalModel) {
      return getLocalModel()
    }
    return getBundledModel()
  }

  /**
   * Returns a model bundled with the IDE.
   * This function, if implemented in a ranking provider where sorting by ML is enabled by default,
   * should use [useExperimentalModel] function to return an appropriate model.
   *
   * For providers that only have one experimental model, just returning that model will suffice.
   */
  protected abstract fun getBundledModel(): DecisionFunction

  protected abstract val supportedTab: SearchEverywhereTab.TabWithMlRanking

  protected val useExperimentalModel: Boolean
    get() = supportedTab.useExperimentalModel

  private fun shouldProvideLocalModel(): Boolean {
    return LocalRankingModelProviderUtil.isPathToLocalModelSpecified(supportedTab)
  }

  private fun getLocalModel(): DecisionFunction {
    return LocalRankingModelProviderUtil.getLocalModel(supportedTab)!!
  }

  protected fun getCatBoostModel(resourceDirectory: String, modelDirectory: String): SearchEverywhereCatBoostModel {
    return CatBoostModelFactory()
      .withModelDirectory(modelDirectory)
      .withResourceDirectory(resourceDirectory)
      .buildModel()
  }
}