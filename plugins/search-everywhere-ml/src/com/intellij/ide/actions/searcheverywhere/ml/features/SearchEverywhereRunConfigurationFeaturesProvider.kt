package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.ide.actions.searcheverywhere.RunConfigurationsSEContributor

class SearchEverywhereRunConfigurationFeaturesProvider : SearchEverywhereElementFeaturesProvider(RunConfigurationsSEContributor::class.java) {
  companion object {
    private const val IS_SHARED = "isShared"
    private const val IS_TEMPORARY = "isTemporary"
    private const val RUN_CONFIGURATION_TYPE = "runConfigType"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    if (element !is ChooseRunConfigurationPopup.ItemWrapper<*> || element.value !is RunnerAndConfigurationSettings) {
      return emptyMap()
    }

    val settings = element.value as RunnerAndConfigurationSettings
    return hashMapOf(
      IS_SHARED to settings.isShared,
      IS_TEMPORARY to settings.isTemporary,
      RUN_CONFIGURATION_TYPE to settings.type.id,
    )
  }
}