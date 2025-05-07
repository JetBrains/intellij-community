// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.ml.api.feature.Feature
import com.jetbrains.ml.api.feature.FeatureDeclaration
import com.jetbrains.ml.api.feature.FeatureFilter
import com.jetbrains.ml.api.feature.extractFeatureDeclarations
import com.jetbrains.ml.api.model.MLModel
import com.jetbrains.ml.api.model.MLModelLoader
import com.jetbrains.ml.models.PythonImportsRankingModelHolder
import com.jetbrains.ml.tools.model.MLModelLoaders
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.ml.tools.model.ModelDistributionReaders
import com.jetbrains.ml.tools.model.catboost.CatBoostDistributionFormat
import com.jetbrains.ml.tools.model.suspendable.MLModelSuspendableService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService


@Service
class ImportsRankingModelService : MLModelSuspendableService<MLModel<Double>, Double>(
  MissingTypingFeaturesLoader(MLModelLoaders.Regressor(
    reader = ModelDistributionReaders.FromJavaResources(PythonImportsRankingModelHolder::class.java, PythonImportsRankingModelHolder.MODEL_ID),
    format = CatBoostDistributionFormat(),
  ))
)

private class QuickfixRankingModelLoading : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!service<FinalImportRankingStatusService>().shouldLoadModel) return
    try {
      service<ImportsRankingModelService>().loadModel()
    } catch (e: RuntimeException) {
      thisLogger().error("Failed to load python imports ranking model", e)
    }
  }
}

private class MissingTypingFeaturesLoader(private val baseLoader: MLModelLoader<MLModel<Double>, Double>) : MLModelLoader<MLModel<Double>, Double> {

  override fun loadModel(executor: ExecutorService, parameters: Map<String, Any>?): CompletableFuture<MLModel<Double>> {
    return baseLoader.loadModel(executor, parameters).thenApply { rawModel ->
      return@thenApply object : MLModel<Double> {
        override fun predict(features: List<Feature>, parameters: Map<String, Any>): Double {
          return rawModel.predict(features + fillTypingFeatures(), parameters)
        }

        override fun predictBatch(contextFeatures: List<Feature>, itemFeatures: List<List<Feature>>, parameters: Map<String, Any>): List<Double> {
          return rawModel.predictBatch(contextFeatures + fillTypingFeatures(), itemFeatures, parameters)
        }

        override val knownFeatures: FeatureFilter = object : FeatureFilter {
          override fun accept(featureDeclaration: FeatureDeclaration<*>): Boolean {
            return if (featureDeclaration in allTypingFeatures) {
              return false
            }
            else {
              rawModel.knownFeatures.accept(featureDeclaration)
            }
          }
        }
      }
    }
  }
}

private object TypingFeatures {
  val SINCE_LAST_TYPING = FeatureDeclaration.int("typing_speed_tracker_time_since_last_typing") { "Deprecated" }
  val TYPING_SPEED_1S = FeatureDeclaration.double("typing_speed_tracker_typing_speed_1s") { "Deprecated" }
  val TYPING_SPEED_2S = FeatureDeclaration.double("typing_speed_tracker_typing_speed_2s") { "Deprecated" }
  val TYPING_SPEED_5S = FeatureDeclaration.double("typing_speed_tracker_typing_speed_5s") { "Deprecated" }
  val TYPING_SPEED_30S = FeatureDeclaration.double("typing_speed_tracker_typing_speed_30s") { "Deprecated" }
}

private val allTypingFeatures: List<FeatureDeclaration<*>> = extractFeatureDeclarations(TypingFeatures)

private fun fillTypingFeatures(): List<Feature> {
  return listOf(
    TypingFeatures.SINCE_LAST_TYPING with 1,
    TypingFeatures.TYPING_SPEED_2S with 0.0,
    TypingFeatures.TYPING_SPEED_30S with 0.0,
    TypingFeatures.TYPING_SPEED_5S with 0.0,
    TypingFeatures.TYPING_SPEED_1S with 0.0,
  )
}
