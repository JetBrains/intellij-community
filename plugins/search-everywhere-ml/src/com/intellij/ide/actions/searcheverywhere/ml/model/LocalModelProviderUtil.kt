package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereTabWithMl
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.models.local.LocalRandomForestModel
import com.intellij.internal.ml.models.local.ZipModelMetadataReader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import java.util.zip.ZipFile

internal object LocalRankingModelProviderUtil {
  private val LOG = Logger.getInstance(this.javaClass)

  fun getLocalModel(contributorId: String): DecisionFunction? {
    val tab = SearchEverywhereTabWithMl.findById(contributorId) ?: return null
    if (!isPathToLocalModelSpecified(tab)) return null

    val path = getPath(tab)
    return loadLocalModelFromZip(path)
  }

  fun isPathToLocalModelSpecified(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMl.findById(tabId) ?: return false
    return isPathToLocalModelSpecified(tab)
  }

  private fun isPathToLocalModelSpecified(tab: SearchEverywhereTabWithMl) = Registry.get(getRegistryKey(tab)).isChangedFromDefault

  private fun getRegistryKey(tab: SearchEverywhereTabWithMl) = "search.everywhere.ml.${tab.name.lowercase()}.model.path"

  private fun getPath(tab: SearchEverywhereTabWithMl) = Registry.stringValue(getRegistryKey(tab))

  private fun loadLocalModelFromZip(path: String): DecisionFunction? {
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