// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.collector

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo
import com.jetbrains.python.newProjectWizard.collector.PyProjectTypeValidationRule.Companion.EMPTY_PROJECT_TYPE_ID
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Ensures project type provided to [doValidate] is [PyProjectTypeGenerator.projectTypeForStatistics]
 */
@Internal
class PyProjectTypeValidationRule : CustomValidationRule() {
  companion object {
    /**
     * [com.intellij.platform.DirectoryProjectGenerator] for default (empty, base) project type isn't registered in EP, hence hardcoded
     */
    const val EMPTY_PROJECT_TYPE_ID = "com.intellij.pycharm.community.ide.impl.newProject.steps.PythonBaseProjectGenerator"
  }

  override fun getRuleId(): String = "python_new_project_type"

  override fun doValidate(data: String, context: EventContext): ValidationResultType = validate(data)
}

@Internal
fun validate(data: String): ValidationResultType {
  val valid = data == EMPTY_PROJECT_TYPE_ID || AbstractNewProjectStep.EP_NAME
    .extensionList
    .filterIsInstance<PyProjectTypeGenerator>()
    .any { getPluginInfo(it::class.java).isDevelopedByJetBrains() && it.projectTypeForStatistics == data }
  return if (valid) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}