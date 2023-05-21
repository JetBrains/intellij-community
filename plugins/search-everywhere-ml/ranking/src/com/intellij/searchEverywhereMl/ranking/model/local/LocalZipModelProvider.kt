package com.intellij.searchEverywhereMl.ranking.model.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.models.local.LocalRandomForestModel
import com.intellij.internal.ml.models.local.ZipModelMetadataReader
import com.intellij.openapi.diagnostic.Logger
import java.util.zip.ZipFile

internal class LocalZipModelProvider : LocalModelProvider {
  private val LOG = Logger.getInstance(this.javaClass)

  override fun loadModel(path: String): DecisionFunction? {
    try {
      ZipFile(path).use { file ->
        val metadataReader = ZipModelMetadataReader(file)
        val metadata = FeaturesInfo.buildInfo(metadataReader)
        val modelText = metadataReader.resourceContent("model.txt")
        return LocalRandomForestModel.loadModel(modelText, metadata)
      }
    }
    catch (t: Throwable) {
      LOG.error(t)
      return null
    }
  }
}
