// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.jetbrains.python.PyBundle.message

class PythonUserJobFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialog<CommonFeedbackSystemData>(project, forTest) {

  override val myFeedbackReportId: String
    get() = "python_user_job_survey"


  override val myTitle: String
    get() = message("python.survey.user.job.dialog.title")


  val items = listOf(
    CheckBoxItemData(message("python.survey.user.job.dialog.blocks.checkbox.data"), "data_analysis"),
    CheckBoxItemData(message("python.survey.user.job.dialog.blocks.checkbox.ml"), "ml"),
    CheckBoxItemData(message("python.survey.user.job.dialog.blocks.checkbox.web"), "web_dev"),
    CheckBoxItemData(message("python.survey.user.job.dialog.blocks.checkbox.scripts"), "scripts"),
  )

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(message("python.survey.user.job.dialog.blocks.top")),
    CheckBoxGroupBlock(message("python.survey.user.job.dialog.blocks.checkbox.group"), items, "python_job_checkbox")
      .addOtherTextField(message("python.survey.user.job.dialog.blocks.checkbox.other"))
      .requireAnswer(),
  )

  override val mySystemInfoData: CommonFeedbackSystemData
    get() = CommonFeedbackSystemData.getCurrentData()

  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  override fun sendFeedbackData() {
    super.sendFeedbackData()

    val builder = StringBuilder()
    (myBlocks[1] as CheckBoxGroupBlock).collectBlockTextDescription(builder)
    val selectedItems = items.filter { it.property }.map { it.jsonElementName }
    val other = builder.toString().lines()[5].substringAfter(message("python.survey.user.job.dialog.blocks.checkbox.other") + ": ")

    PythonJobStatisticsCollector.logJobEvent(selectedItems, other)
  }

  init {
    init()
  }
}