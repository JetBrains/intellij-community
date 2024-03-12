// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import org.jetbrains.plugins.terminal.TerminalBundle

internal class BlockTerminalFeedbackDialog(project: Project, forTest: Boolean) : BlockBasedFeedbackDialog<CommonFeedbackSystemData>(project, forTest) {
  override val myFeedbackReportId: String = "new_terminal"

  override val myTitle: String = TerminalBundle.message("feedback.dialog.title")

  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }

  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(TerminalBundle.message("feedback.dialog.header")),
    DescriptionBlock(TerminalBundle.message("feedback.dialog.description")),
    RatingGroupBlock(TerminalBundle.message("feedback.dialog.rating.title"), createRatingItems())
      .setHint(TerminalBundle.message("feedback.dialog.rating.description"))
      .setRandomOrder(true),
    TextAreaBlock(TerminalBundle.message("feedback.dialog.other"), "other")
      .setPlaceholder(TerminalBundle.message("feedback.dialog.other.placeholder"))
  )

  private fun createRatingItems(): List<RatingItem> = listOf(
    RatingItem(TerminalBundle.message("feedback.dialog.rating.interface"), "interface"),
    RatingItem(TerminalBundle.message("feedback.dialog.rating.feature.set"), "feature_set"),
    RatingItem(TerminalBundle.message("feedback.dialog.rating.performance"), "performance"),
  )

  init {
    init()
  }
}