package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.actions.searcheverywhere.RunConfigurationsSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereRunConfigurationFeaturesProvider.Fields.IS_SHARED
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereRunConfigurationFeaturesProvider.Fields.IS_TEMPORARY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereRunConfigurationFeaturesProvider.Fields.RUN_CONFIGURATION_TYPE

private class SearchEverywhereRunConfigurationFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(RunConfigurationsSEContributor::class.java) {
  object Fields {
    val IS_SHARED = EventFields.Boolean("isShared")
    val IS_TEMPORARY = EventFields.Boolean("isTemporary")
    val RUN_CONFIGURATION_TYPE = EventFields.StringValidatedByCustomRule("runConfigType", SearchEverywhereRunConfigurationTypeValidator::class.java)
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(IS_SHARED, IS_TEMPORARY, RUN_CONFIGURATION_TYPE)
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    if (element !is ChooseRunConfigurationPopup.ItemWrapper<*> || element.value !is RunnerAndConfigurationSettings) {
      return emptyList()
    }

    val settings = element.value as RunnerAndConfigurationSettings
    return listOf(
      IS_SHARED.with(settings.isShared),
      IS_TEMPORARY.with(settings.isTemporary),
      RUN_CONFIGURATION_TYPE.with(settings.type.id),
    )
  }
}

private class SearchEverywhereRunConfigurationTypeValidator : CustomValidationRule() {
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