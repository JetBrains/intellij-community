package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.RunConfigurationUtilValidator
import com.intellij.ide.actions.searcheverywhere.RunConfigurationsSEContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair

class SearchEverywhereRunConfigurationFeaturesProvider : SearchEverywhereElementFeaturesProvider(RunConfigurationsSEContributor::class.java) {
  companion object {
    private val IS_SHARED = EventFields.Boolean("isShared")
    private val IS_TEMPORARY = EventFields.Boolean("isTemporary")
    private val RUN_CONFIGURATION_TYPE =
      EventFields.StringValidatedByCustomRule("runConfigType", RunConfigurationUtilValidator::class.java)
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(IS_SHARED, IS_TEMPORARY, RUN_CONFIGURATION_TYPE)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): List<EventPair<*>> {
    if (element !is ChooseRunConfigurationPopup.ItemWrapper<*> || element.value !is RunnerAndConfigurationSettings) {
      return emptyList()
    }

    val settings = element.value as RunnerAndConfigurationSettings
    return arrayListOf(
      IS_SHARED.with(settings.isShared),
      IS_TEMPORARY.with(settings.isTemporary),
      RUN_CONFIGURATION_TYPE.with(settings.type.id),
    )
  }
}