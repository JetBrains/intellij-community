// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle.message
import kotlinx.datetime.LocalDate

class PythonJobSurveyConfig : InIdeFeedbackSurveyConfig {

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return PythonUserJobFeedbackDialog(project, forTest)
  }

  override val surveyId: String = "python_user_job_survey"
  override val requireIdeEAP: Boolean = true
  override val lastDayOfFeedbackCollection: LocalDate
    get() = LocalDate(2024, 12, 31)

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isPyCharmCommunity()
  override fun checkExtraConditionSatisfied(project: Project): Boolean = shouldShowSurvey()

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      message("python.survey.user.job.notification.group"),
      message("python.survey.user.job.notification.title"),
      message("python.survey.user.job.notification.content"),
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {

  }

  override fun updateStateAfterDialogClosedOk(project: Project) {

  }
}