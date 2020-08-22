// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.settings

import com.intellij.completion.ranker.ExperimentModelProvider
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

object MLCompletionSettingsCollector {
  private val COUNTER_GROUP = EventLogGroup("ml.completion", 1)
  private val rankerIdField: StringEventField = EventFields.String("ranker_id").withCustomRule("ml_completion_ranker_id")
  private val enabledField: BooleanEventField = EventFields.Boolean("enabled")
  private val enabledByDefaultField: BooleanEventField = EventFields.Boolean("enabled_by_default")
  private val languageCheckboxUsedField: BooleanEventField = EventFields.Boolean("using_language_checkbox")

  private val LANGUAGE_SETTINGS_CHANGED = COUNTER_GROUP.registerVarargEvent("ranking.settings.changed",
                                                                            rankerIdField,
                                                                            enabledField,
                                                                            enabledByDefaultField,
                                                                            languageCheckboxUsedField)

  private val DECORATION_SETTINGS_CHANGED = COUNTER_GROUP.registerEvent("decorating.settings.changed", EventFields.Boolean("enabled"))

  @JvmStatic
  fun rankingSettingsChanged(rankerId: String,
                             enabled: Boolean,
                             enabledByDefault: Boolean,
                             usingLanguageCheckbox: Boolean) {
    LANGUAGE_SETTINGS_CHANGED.log(
      rankerIdField.with(rankerId),
      enabledField.with(enabled),
      enabledByDefaultField.with(enabledByDefault),
      languageCheckboxUsedField.with(usingLanguageCheckbox)
    )
  }

  @JvmStatic
  fun decorationSettingChanged(enabled: Boolean) {
    DECORATION_SETTINGS_CHANGED.log(enabled)
  }

  class MLRankingSettingsValidationRule : CustomValidationRule() {
    override fun acceptRuleId(ruleId: String?): Boolean = ruleId == "ml_completion_ranker_id"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (ExperimentModelProvider.availableProviders().any { it.id == data }) {
        return ValidationResultType.ACCEPTED
      }

      return ValidationResultType.REJECTED
    }
  }
}
