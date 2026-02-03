// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.platform.ide.impl.customization.currentOsNameForIntelliJSupport
import com.intellij.util.PlatformUtils
import com.intellij.util.Urls
import com.jetbrains.python.PyBundle
import kotlinx.datetime.LocalDate

internal class PyCharmUxSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: ExternalFeedbackSurveyType<*> =
    ExternalFeedbackSurveyType(PyCharmUxSurveyConfig())
}

private class PyCharmUxSurveyConfig : ExternalFeedbackSurveyConfig {
  override val surveyId: String = "pycharm_ux_survey_2026"

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(
    year = 2026,
    monthNumber = 3,
    dayOfMonth = 31,
  )

  override val requireIdeEAP: Boolean = false

  override fun getUrlToSurvey(project: Project): String =
    Urls.newFromEncoded("https://surveys.jetbrains.com/s3/pycharm-ux-survey")
      .addParameters(
        mapOf(
          "build" to ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode(),
          "os" to currentOsNameForIntelliJSupport(),
        )
      )
      .toExternalForm()

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification =
    RequestFeedbackNotification(
      "Feedback in IDE",
      PyBundle.message("pycharm.statistics.feedback.ux.survey.title"),
      PyBundle.message("pycharm.statistics.feedback.ux.survey.content")
    )

  override fun updateStateAfterNotificationShowed(project: Project) {
    service<PyCharmUxSurveyStore>().state.wasShown = true
  }

  override fun checkIdeIsSuitable(): Boolean =
    PlatformUtils.isPyCharm() && !PlatformUtils.isDataSpell()

  override fun checkExtraConditionSatisfied(project: Project): Boolean =
    !service<PyCharmUxSurveyStore>().state.wasShown

  override fun updateStateAfterRespondActionInvoked(project: Project) {
    // no op
  }
}

@Service
@State(name = "PyCharmUxSurveyStore", storages = [Storage("pycharm-ux-survey.xml")])
private class PyCharmUxSurveyStore : SimplePersistentStateComponent<PyCharmUxSurveyStore.State>(State()) {
  class State : BaseState() {
    var wasShown by property(false)
  }
}