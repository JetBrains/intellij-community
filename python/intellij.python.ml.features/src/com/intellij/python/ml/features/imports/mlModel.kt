// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.codeInsight.inline.completion.ml.MLUnitTyping
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ml.impl.MLModelSelector
import com.intellij.python.ml.features.imports.MissingTypingFeaturesLoader.Companion.fillingTypingFeatures
import com.jetbrains.ml.building.blocks.model.MLModel
import com.jetbrains.ml.building.blocks.model.MLModelLoader
import com.jetbrains.ml.building.blocks.model.catboost.DistributionFormat
import com.jetbrains.ml.building.blocks.model.catboost.ModelDistributionReaders
import com.jetbrains.ml.building.blocks.model.classical.MLModelLoaders
import com.jetbrains.ml.features.api.MLUnit
import com.jetbrains.ml.features.api.feature.Feature
import com.jetbrains.ml.features.api.feature.FeatureDeclaration
import java.nio.file.Path


@Service
class MLModelService : MLModelSelector<MLModel<Double>, Double>(
  defaultLoader = MLModelLoaders.Regressor(
    reader = ModelDistributionReaders.FromJavaResources(MLModelService::class.java, Path.of("python-imports-ranking-model")),
    format = DistributionFormat(),
  ).fillingTypingFeatures(),
  registryModelLoaderFactory = { path ->
    MLModelLoaders.Regressor(
      reader = ModelDistributionReaders.FromDirectory(path),
      format = DistributionFormat(),
    ).fillingTypingFeatures()
  },
  modelPathRegistryKey = "quickfix.ranking.ml.model.path",
  coroutineScope = service<MLApiComputations>().coroutineScope,
)

private class MissingTypingFeaturesLoader(private val baseLoader: MLModelLoader<MLModel<Double>, Double>) : MLModelLoader<MLModel<Double>, Double> by baseLoader {
  override suspend fun loadModel(features: Map<MLUnit<*>, List<FeatureDeclaration<*>>>, parameters: Map<String, Any>?): MLModel<Double> {
    val model = baseLoader.loadModel(features + mapOf(
      MLUnitTyping to listOf(
        TypingFeatures.SINCE_LAST_TYPING,
        TypingFeatures.TYPING_SPEED_1S,
        TypingFeatures.TYPING_SPEED_2S,
        TypingFeatures.TYPING_SPEED_30S,
        TypingFeatures.TYPING_SPEED_5S
      )
    ), parameters)

    return object : MLModel<Double> by model {
      override fun predict(features: Map<MLUnit<*>, List<Feature>>, parameters: Map<String, Any>): Double {
        return model.predict(features + mapOf(MLUnitTyping to fillTypingFeatures()), parameters)
      }

      override fun predictBatch(contextFeatures: Map<MLUnit<*>, List<Feature>>, itemFeatures: List<Map<MLUnit<*>, List<Feature>>>, parameters: Map<String, Any>): List<Double> {
        return model.predictBatch(contextFeatures + mapOf(MLUnitTyping to fillTypingFeatures()), itemFeatures, parameters)
      }
    }
  }

  companion object {
    fun MLModelLoader<MLModel<Double>, Double>.fillingTypingFeatures(): MLModelLoader<MLModel<Double>, Double> {
      return MissingTypingFeaturesLoader(this)
    }
  }
}

/**
 * TODO: Should be removed, because they don't exist anymore
 */
internal object TypingFeatures {
  val SINCE_LAST_TYPING = FeatureDeclaration.int("time_since_last_typing")
  val TYPING_SPEED_1S = FeatureDeclaration.double("typing_speed_1s")
  val TYPING_SPEED_2S = FeatureDeclaration.double("typing_speed_2s")
  val TYPING_SPEED_30S = FeatureDeclaration.double("typing_speed_30s")
  val TYPING_SPEED_5S = FeatureDeclaration.double("typing_speed_5s")
}

private class QuickfixRankingModelLoading : ProjectActivity {
  override suspend fun execute(project: Project) {
    service<MLModelService>().prepareModel(MLTaskPyCharmImportStatementsRanking)
  }
}

private fun fillTypingFeatures(): List<Feature> {
  return listOf(
    TypingFeatures.SINCE_LAST_TYPING with 1,
    TypingFeatures.TYPING_SPEED_2S with 0.0,
    TypingFeatures.TYPING_SPEED_30S with 0.0,
    TypingFeatures.TYPING_SPEED_5S with 0.0,
    TypingFeatures.TYPING_SPEED_1S with 0.0,
  )
}
