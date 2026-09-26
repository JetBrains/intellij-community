// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl.migration

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonItemData
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics

internal class ClassicTerminalFeedbackDialog(
  private val project: Project,
  forTest: Boolean,
) : BlockBasedFeedbackDialog<ClassicTerminalFeedbackData>(project, forTest) {
  override val myFeedbackReportId: String = "classic_terminal_switch_back"

  override val myTitle: String = TerminalBundle.message("classic.switch.back.feedback.dialog.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(TerminalBundle.message("classic.switch.back.feedback.dialog.header")),
    DescriptionBlock(TerminalBundle.message("classic.switch.back.feedback.dialog.description")),

    RadioButtonGroupBlock(
      TerminalBundle.message("classic.switch.back.feedback.dialog.reason.label"),
      reasonItems(),
      "switch_back_reason",
    ).requireAnswer(),

    TextAreaBlock(TerminalBundle.message("classic.switch.back.feedback.dialog.details.label"), "details")
      .setPlaceholder(TerminalBundle.message("classic.switch.back.feedback.dialog.details.placeholder")),
  )

  private fun reasonItems(): List<RadioButtonItemData> = listOf(
    RadioButtonItemData(TerminalBundle.message("classic.switch.back.feedback.dialog.reason.performance"), "performance"),
    RadioButtonItemData(TerminalBundle.message("classic.switch.back.feedback.dialog.reason.missing.feature"), "failed_expectations"),
    RadioButtonItemData(TerminalBundle.message("classic.switch.back.feedback.dialog.reason.appearance"), "appearance"),
    RadioButtonItemData(TerminalBundle.message("classic.switch.back.feedback.dialog.reason.other"), "other"),
  )

  override suspend fun computeSystemInfoData(): ClassicTerminalFeedbackData = withContext(Dispatchers.IO) { // for shellPath
    ClassicTerminalFeedbackData(
      selectedShell = TerminalShellInfoStatistics.getShellNameForStat(TerminalProjectOptionsProvider.getInstance(project).shellPath),
      systemInfo = CommonFeedbackSystemData.getCurrentData()
    )
  }

  @Suppress("HardCodedStringLiteral")
  override fun showFeedbackSystemInfoDialog(systemInfoData: ClassicTerminalFeedbackData) {
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
internal data class ClassicTerminalFeedbackData(
  @get:NlsSafe val selectedShell: String,
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