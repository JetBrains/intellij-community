package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.searchEverywhereMl.ranking.SearchEverywhereMLStatisticsCollector

class SearchEverywhereMlElementFeatureValidationRule : CustomValidationRule() {
  override fun getRuleId(): String {
    return "mlse_element_feature"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (isThirdPartyValue(data)) return ValidationResultType.THIRD_PARTY
    return when (SearchEverywhereMLStatisticsCollector.Fields.findElementFeatureByName(data)) {
      null -> ValidationResultType.REJECTED
      else -> ValidationResultType.ACCEPTED
    }
  }
}