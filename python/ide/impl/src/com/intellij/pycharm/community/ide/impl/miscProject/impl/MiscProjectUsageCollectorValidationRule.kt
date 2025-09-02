// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class MiscProjectUsageCollectorValidationRule : CustomValidationRule() {

  override fun getRuleId(): String = "misc_project_type"

  override fun doValidate(data: String, context: EventContext): ValidationResultType = validate(data)
}

@Internal
fun validate(technicalNameForStatistics: String): ValidationResultType {
  val valid = (MiscFileType.EP
                 .extensionList + listOf(MiscScriptFileType))
    .any { getPluginInfo(it::class.java).isDevelopedByJetBrains() && it.technicalNameForStatistics == technicalNameForStatistics }
  return if (valid) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}