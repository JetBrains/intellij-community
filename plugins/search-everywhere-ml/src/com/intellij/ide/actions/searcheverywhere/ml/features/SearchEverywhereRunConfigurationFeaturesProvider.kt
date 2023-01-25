package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.actions.searcheverywhere.RunConfigurationsSEContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo

internal class SearchEverywhereRunConfigurationFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(RunConfigurationsSEContributor::class.java) {
  companion object {
    private val IS_SHARED = EventFields.Boolean("isShared")
    private val IS_TEMPORARY = EventFields.Boolean("isTemporary")
    private val RUN_CONFIGURATION_TYPE =
      EventFields.StringValidatedByCustomRule("runConfigType", RunConfigurationTypeValidator::class.java)
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf(IS_SHARED, IS_TEMPORARY, RUN_CONFIGURATION_TYPE)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
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

  class RunConfigurationTypeValidator : CustomValidationRule() {
    override fun getRuleId(): String {
      return "run_config_type"
    }

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED
      val configurationType = findConfigurationTypeById(data) ?: return ValidationResultType.REJECTED
      return if (getPluginInfo(configurationType.javaClass).isDevelopedByJetBrains()) ValidationResultType.ACCEPTED
      else ValidationResultType.THIRD_PARTY
    }

    private fun findConfigurationTypeById(data: String): ConfigurationType? {
      return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.find { type: ConfigurationType -> type.id == data }
    }
  }
}