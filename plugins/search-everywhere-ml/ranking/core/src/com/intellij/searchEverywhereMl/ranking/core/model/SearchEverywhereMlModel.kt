package com.intellij.searchEverywhereMl.ranking.core.model

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.catboost.CatBoostModelMetadataReader
import com.intellij.internal.ml.catboost.CatBoostResourcesModelMetadataReader
import com.intellij.internal.ml.catboost.NaiveCatBoostModel
import com.intellij.internal.ml.models.local.LocalCatBoostModelMetadataReader
import org.jetbrains.annotations.NonNls

internal abstract class SearchEverywhereMlModel(private val featuresInfo: FeaturesInfo) : DecisionFunction {
  override fun getFeaturesOrder(): Array<out FeatureMapper?> = featuresInfo.featuresOrder

  override fun getRequiredFeatures(): List<String?> = emptyList()

  override fun getUnknownFeatures(features: Collection<String?>): List<String?> = emptyList()

  override fun version(): @NonNls String? = featuresInfo.version

  fun predict(features: Map<String, Any?>): Double {
    val mappedFeatures = buildArray(featuresInfo.featuresOrder, features)
    return predict(mappedFeatures)
  }

  private fun buildArray(featuresOrder: Array<FeatureMapper>, features: Map<String, Any?>): DoubleArray {
    val array = DoubleArray(featuresOrder.size)
    for (i in featuresOrder.indices) {
      val mapper = featuresOrder[i]
      val value = features[mapper.featureName]
      array[i] = mapper.asArrayValue(value)
    }
    return array
  }
}

internal open class SearchEverywhereCatBoostModel(
  private val model: NaiveCatBoostModel,
  featuresInfo: FeaturesInfo,
) : SearchEverywhereMlModel(featuresInfo) {
  override fun predict(features: DoubleArray?): Double {
    return model.makePredict(features)
  }
}

internal class SearchEverywhereCatBoostBinaryClassifierModel(
  model: NaiveCatBoostModel,
  featuresInfo: FeaturesInfo,
  private val trueThreshold: Double,
) : SearchEverywhereCatBoostModel(model, featuresInfo) {
  fun predictTrue(features: Map<String, Any?>): Boolean {
    return predict(features) >= trueThreshold
  }
}

internal class CatBoostModelFactory {
  private var modelDirectory: String? = null
  private var resourceDirectory: String? = null

  fun withModelDirectory(modelDirectory: String) = apply {
    this.modelDirectory = modelDirectory
  }

  fun withResourceDirectory(resourceDirectory: String) = apply {
    this.resourceDirectory = resourceDirectory
  }

  fun buildModel(): SearchEverywhereCatBoostModel {
    return buildModelFromMetadata(
      metadataReader = ::getResourceMetadataReader,
      model = ::SearchEverywhereCatBoostModel,
    )
  }

  fun buildLocalModel(): SearchEverywhereCatBoostModel {
    return buildModelFromMetadata(
      metadataReader = ::getLocalMetadataReader,
      model = ::SearchEverywhereCatBoostModel,
    )
  }

  fun buildBinaryClassifier(trueThreshold: Double): SearchEverywhereCatBoostBinaryClassifierModel {
    return buildModelFromMetadata(
      metadataReader = ::getResourceMetadataReader,
      model = { model, features -> SearchEverywhereCatBoostBinaryClassifierModel(model, features, trueThreshold) }
    )
  }

  private fun <T : SearchEverywhereCatBoostModel> buildModelFromMetadata(
    metadataReader: (resourceDirectory: String, modelDirectory: String) -> CatBoostModelMetadataReader,
    model: (model: NaiveCatBoostModel, metadata: FeaturesInfo) -> T,
  ): T {
    val resourceDirectory = checkNotNull(resourceDirectory)
    val modelDirectory = checkNotNull(modelDirectory)

    val reader = metadataReader(resourceDirectory, modelDirectory)
    val naiveModel = reader.loadModel()
    val metadata = FeaturesInfo.buildInfo(reader)

    return model(naiveModel, metadata)
  }

  private fun getResourceMetadataReader(resourceDirectory: String, modelDirectory: String): CatBoostResourcesModelMetadataReader {
    return CatBoostResourcesModelMetadataReader(this::class.java, resourceDirectory, modelDirectory)
  }

  private fun getLocalMetadataReader(resourceDirectory: String, modelDirectory: String): LocalCatBoostModelMetadataReader {
    return LocalCatBoostModelMetadataReader(modelDirectory, resourceDirectory)
  }
}

