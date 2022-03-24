// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.catboost.CatBoostResourcesModelMetadataReader
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Loads ML model from module dependency or local file, loaded models predict relevance of each element in Search Everywhere tab
 */
internal abstract class SearchEverywhereMLRankingModelLoader {
  companion object {
    private val EP_NAME: ExtensionPointName<SearchEverywhereMLRankingModelLoader>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.rankingModelLoader")

    fun getForTab(contributorId: String): SearchEverywhereMLRankingModelLoader {
      return EP_NAME.findFirstSafe {
        it.supportedContributor.simpleName == contributorId
      } ?: throw IllegalArgumentException("Unsupported contributor $contributorId")
    }
  }

  /**
   * Returns a model used for ranking.
   * If a path to a local model is specified, a local model will be returned, provided that the user is in an experimental group.
   * This is so that, a new model can be easily compared to an existing one.
   *
   * If no path is specified, then a bundled model will be provided which can either be experimental or standard,
   * depending on the return value of [shouldProvideExperimentalModel].
   */
  fun loadModel(): DecisionFunction {
    if (shouldProvideLocalModel() && shouldProvideExperimentalModel()) {
      return getLocalModel()
    }
    return getBundledModel()
  }

  /**
   * Returns a model bundled with the IDE.
   * This function, if implemented in a ranking provider where sorting by ML is enabled by default,
   * should use [shouldProvideExperimentalModel] function to return an appropriate model.
   *
   * For providers that only have one experimental model, just returning that model will suffice.
   */
  protected abstract fun getBundledModel(): DecisionFunction

  protected abstract val supportedContributor: Class<out SearchEverywhereContributor<*>>

  protected fun shouldProvideExperimentalModel(): Boolean {
    return SearchEverywhereMlSessionService.getService().shouldUseExperimentalModel(supportedContributor.simpleName)
  }

  private fun shouldProvideLocalModel(): Boolean {
    return LocalRankingModelProviderUtil.isPathToLocalModelSpecified(supportedContributor.simpleName)
  }

  private fun getLocalModel(): DecisionFunction {
    return LocalRankingModelProviderUtil.getLocalModel(supportedContributor.simpleName)!!
  }

  protected fun getCatBoostModel(resourceDirectory: String, modelDirectory: String): DecisionFunction {
    val metadataReader = CatBoostResourcesModelMetadataReader(this::class.java, resourceDirectory, modelDirectory)
    val metadata = FeaturesInfo.buildInfo(metadataReader)
    val model = metadataReader.loadModel()

    return object : SearchEverywhereMLRankingDecisionFunction(metadata) {
      override fun predict(features: DoubleArray): Double = model.makePredict(features)
    }
  }
}