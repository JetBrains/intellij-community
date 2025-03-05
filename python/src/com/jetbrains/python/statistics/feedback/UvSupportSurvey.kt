// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonItemData
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle.message
import kotlinx.datetime.LocalDate

class UvSupportSurvey : InIdeFeedbackSurveyConfig {
  private val suitableIdeVersion = "2025.1"

  override val surveyId: String = "python_uv_support_survey"
  override val lastDayOfFeedbackCollection: LocalDate
    get() = LocalDate(2025, 3, 15)

  override val requireIdeEAP: Boolean = true

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isPyCharm()

  override fun updateStateAfterDialogClosedOk(project: Project) {}
  override fun updateStateAfterNotificationShowed(project: Project) {}

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return suitableIdeVersion == ApplicationInfo.getInstance().shortVersion
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return UvSupportSurveyDialog(project, forTest)
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      message("python.survey.uv.support.group"),
      message("python.survey.uv.support.title"),
      message("python.survey.uv.support.content"),
    )
  }
}

class UvSupportSurveyDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialog<CommonFeedbackSystemData>(project, forTest) {

  override val myFeedbackReportId: String
    get() = "python_uv_support_survey"

  override val myTitle: String
    get() = message("python.survey.uv.support.dialog.title")

  private val items = listOf(
    RadioButtonItemData(message("python.survey.uv.support.blocks.radiobutton.group.good"), "good"),
    RadioButtonItemData(message("python.survey.uv.support.blocks.radiobutton.group.soso"), "soso"),
    RadioButtonItemData(message("python.survey.uv.support.blocks.radiobutton.group.bad"), "bad"),
    RadioButtonItemData(message("python.survey.uv.support.blocks.radiobutton.group.notyet"), "notyet")
  )

  override val myBlocks: List<FeedbackBlock>
    get() = listOf(
      TopLabelBlock(message("python.survey.uv.support.dialog.blocks.top")),
      RadioButtonGroupBlock(
        message("python.survey.uv.support.blocks.radiobutton.group"),
        items,
        "python_uv_radiobutton"),
      TextAreaBlock(
        message("python.survey.uv.support.blocks.textarea.group"),
        "python_uv_textarea")
    )

  override val mySystemInfoData: CommonFeedbackSystemData
    get() = CommonFeedbackSystemData.getCurrentData()

  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }
}

class PythonShowUvSupportAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    PythonUvSupportSurvey().showNotification(e.project!!, true)
  }
}

class PythonUvSupportSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(UvSupportSurvey())
}