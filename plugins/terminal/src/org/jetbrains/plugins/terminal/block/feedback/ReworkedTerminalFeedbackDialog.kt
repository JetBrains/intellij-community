// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics

internal class ReworkedTerminalFeedbackDialog(project: Project, forTest: Boolean) : BlockBasedFeedbackDialog<ReworkedTerminalUsageData>(project, forTest) {
  override val myFeedbackReportId: String = "reworked_terminal"

  override val myTitle: String = TerminalBundle.message("feedback.dialog.title")

  override val mySystemInfoData: ReworkedTerminalUsageData by lazy {
    ReworkedTerminalUsageData(
      selectedShell = TerminalShellInfoStatistics.getShellNameForStat(TerminalProjectOptionsProvider.getInstance(project).shellPath),
      feedbackMoment = getFeedbackMoment(project),
      systemInfo = CommonFeedbackSystemData.getCurrentData()
    )
  }

  @Suppress("HardCodedStringLiteral")
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData.systemInfo) {
      row(TerminalBundle.message("feedback.system.info.shell")) {
        label(mySystemInfoData.selectedShell)
      }
      row(TerminalBundle.message("feedback.system.info.moment")) {
        label(mySystemInfoData.feedbackMoment.toString())
      }
    }
  }

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(TerminalBundle.message("feedback.dialog.header")),
    DescriptionBlock(TerminalBundle.message("feedback.dialog.description")),

    RatingBlock(TerminalBundle.message("feedback.dialog.rating.title"), "overall_rating"),

    TextAreaBlock(TerminalBundle.message("feedback.dialog.issues"), "issues"),

    CheckBoxGroupBlock(TerminalBundle.message("feedback.dialog.improvement"), improvementItems().shuffled(), "important_improvement")
      .addOtherTextField(),

    TextAreaBlock(TerminalBundle.message("feedback.dialog.other"), "extra")
      .setPlaceholder(TerminalBundle.message("feedback.dialog.other.placeholder")),
  )

  private fun improvementItems(): List<CheckBoxItemData> = listOf(
    CheckBoxItemData(TerminalBundle.message("feedback.dialog.improvement.compatibility"), "app_compatibility"),
    CheckBoxItemData(TerminalBundle.message("feedback.dialog.improvement.performance"), "performance"),
    CheckBoxItemData(TerminalBundle.message("feedback.dialog.improvement.shell.support"), "shell_support"),
    CheckBoxItemData(TerminalBundle.message("feedback.dialog.improvement.ai.integration"), "ai_integration"),
    CheckBoxItemData(TerminalBundle.message("feedback.dialog.improvement.ide.integration"), "ide_integration"),
  )

  init {
    init()
  }
}

@Serializable
internal data class ReworkedTerminalUsageData(
  @get:NlsSafe val selectedShell: String,
  val feedbackMoment: TerminalFeedbackMoment,
  val systemInfo: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine(TerminalBundle.message("feedback.system.info.shell"))
    appendLine(selectedShell)
    appendLine(TerminalBundle.message("feedback.system.info.moment"))
    appendLine(feedbackMoment.toString())
    append(systemInfo.toString())
  }
}
