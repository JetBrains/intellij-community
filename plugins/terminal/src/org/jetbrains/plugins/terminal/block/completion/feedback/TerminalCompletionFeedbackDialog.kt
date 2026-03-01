// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.ImageBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RatingBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.ui.RoundedIcon
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics
import kotlin.math.min

internal class TerminalCompletionFeedbackDialog(
  private val project: Project,
  forTest: Boolean,
) : BlockBasedFeedbackDialog<TerminalUsageData>(project, forTest) {
  override val myFeedbackReportId: String = "terminal_command_completion"

  override val myTitle: String = TerminalBundle.message("feedback.dialog.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(TerminalBundle.message("completion.feedback.dialog.header")),
    DescriptionBlock(TerminalBundle.message("completion.feedback.dialog.description")),
    imageBlock(),
    RatingBlock(TerminalBundle.message("completion.feedback.dialog.rating"), "rating")
      .doNotRequireAnswer(),
    TextAreaBlock(TerminalBundle.message("completion.feedback.dialog.experience.label"), "feedback")
      .setPlaceholder(TerminalBundle.message("completion.feedback.dialog.experience.placeholder"))
  )

  private fun imageBlock(): ImageBlock {
    val icon = IconLoader.getIcon("icons/completion_illustration.png", TerminalCompletionFeedbackDialog::class.java.classLoader)
    val arc = JBUIScale.scale(16)
    val arcRatio = arc.toDouble() / min(icon.iconWidth, icon.iconHeight)
    val roundedIcon = RoundedIcon(icon, arcRatio)
    val borderColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    val border = RoundedLineBorder(borderColor, arc)
    return ImageBlock(roundedIcon).withBorder(border)
  }

  override suspend fun computeSystemInfoData(): TerminalUsageData {
    return withContext(Dispatchers.IO) { // for shellPath
      TerminalUsageData(
        selectedShell = TerminalShellInfoStatistics.getShellNameForStat(TerminalProjectOptionsProvider.getInstance(project).shellPath),
        systemInfo = CommonFeedbackSystemData.getCurrentData()
      )
    }
  }

  override fun showFeedbackSystemInfoDialog(systemInfoData: TerminalUsageData) {
    showFeedbackSystemInfoDialog(project, systemInfoData.systemInfo) {
      row(TerminalBundle.message("feedback.system.info.shell")) {
        label(systemInfoData.selectedShell)
      }
    }
  }

  init {
    init()
  }
}

@Serializable
internal data class TerminalUsageData(
  @param:NlsSafe val selectedShell: String,
  val systemInfo: CommonFeedbackSystemData,
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine(TerminalBundle.message("feedback.system.info.shell"))
    appendLine(selectedShell)
    append(systemInfo.toString())
  }
}