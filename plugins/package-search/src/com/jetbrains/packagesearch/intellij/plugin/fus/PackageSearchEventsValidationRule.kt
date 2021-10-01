package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class PackageSearchEventsValidationRule : CustomValidationRule() {

    override fun acceptRuleId(ruleId: String?): Boolean = ruleId == FUSGroupIds.GROUP_ID

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
//        data.split(';', ',').forEach {
//            if (!isFeatureValid(it)) return ValidationResultType.REJECTED
//        }
        return ValidationResultType.ACCEPTED
    }
}
