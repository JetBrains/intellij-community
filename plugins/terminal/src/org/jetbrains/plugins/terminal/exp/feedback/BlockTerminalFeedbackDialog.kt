// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.feedback

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
import org.jetbrains.plugins.terminal.exp.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.exp.feedback.BlockTerminalFeedbackMoment.AFTER_USAGE
import org.jetbrains.plugins.terminal.exp.feedback.BlockTerminalFeedbackMoment.ON_DISABLING

internal class BlockTerminalFeedbackDialog(project: Project, forTest: Boolean) : BlockBasedFeedbackDialog<BlockTerminalUsageData>(project, forTest) {
  override val myFeedbackReportId: String = "new_terminal"

  override val myTitle: String = TerminalBundle.message("feedback.dialog.title")

  override val mySystemInfoData: BlockTerminalUsageData by lazy {
    val usageStorage = TerminalUsageLocalStorage.getInstance()

    BlockTerminalUsageData(
      mostUsedShell = usageStorage.mostUsedShell,
      executedCommandsNumber = usageStorage.executedCommandsNumber,
      feedbackMoment = if (myProject!!.getUserData(BLOCK_TERMINAL_DISABLING) == true) ON_DISABLING else AFTER_USAGE,
      systemInfo = CommonFeedbackSystemData.getCurrentData()
    )
  }

  @Suppress("HardCodedStringLiteral")
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData.systemInfo) {
      row(TerminalBundle.message("feedback.system.info.shell")) {
        label(mySystemInfoData.mostUsedShell)
      }
      row(TerminalBundle.message("feedback.system.info.commands.number")) {
        label(mySystemInfoData.executedCommandsNumber.toString())
      }
      row(TerminalBundle.message("feedback.system.info.moment")) {
        label(mySystemInfoData.feedbackMoment.toString())
      }
    }
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

@Serializable
internal data class BlockTerminalUsageData(
  @NlsSafe val mostUsedShell: String,
  val executedCommandsNumber: Int,
  val feedbackMoment: BlockTerminalFeedbackMoment,
  val systemInfo: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine(TerminalBundle.message("feedback.system.info.shell"))
    appendLine(mostUsedShell)
    appendLine(TerminalBundle.message("feedback.system.info.commands.number"))
    appendLine(executedCommandsNumber)
    appendLine(TerminalBundle.message("feedback.system.info.moment"))
    appendLine(feedbackMoment.toString())
    append(systemInfo.toString())
  }
}

internal enum class BlockTerminalFeedbackMoment {
  ON_DISABLING, AFTER_USAGE
}