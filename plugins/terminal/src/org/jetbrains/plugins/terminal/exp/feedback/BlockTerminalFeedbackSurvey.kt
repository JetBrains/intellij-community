// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.OnDemandFeedbackResolver
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.exp.TerminalUsageLocalStorage

/** Used to indicate that we are trying to show the feedback notification after block terminal is disabled */
internal val BLOCK_TERMINAL_DISABLING: Key<Boolean> = Key.create("BlockTerminalDisabling")

internal fun showBlockTerminalFeedbackNotification(project: Project) {
  project.putUserData(BLOCK_TERMINAL_DISABLING, true)
  val shown = OnDemandFeedbackResolver.getInstance().showFeedbackNotification(BlockTerminalFeedbackSurvey::class, project)
  if (!shown) {
    project.putUserData(BLOCK_TERMINAL_DISABLING, null)
  }
}

internal class BlockTerminalFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(BlockTerminalSurveyConfig())
}

internal class BlockTerminalSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "new_terminal"

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return BlockTerminalFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    // do nothing
  }

  // Last date is an approximate time of 2024.2 release
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, 8, 1)

  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val usageStorage = TerminalUsageLocalStorage.getInstance()
    return !usageStorage.state.feedbackNotificationShown &&
           // show notification if user executed enough commands or if block terminal is being disabled
           (usageStorage.executedCommandsNumber >= 15 || usageStorage.executedCommandsNumber > 0 && project.getUserData(BLOCK_TERMINAL_DISABLING) == true)
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification("Feedback In IDE",
                                       TerminalBundle.message("feedback.notification.title"),
                                       TerminalBundle.message("feedback.notification.text"))
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    TerminalUsageLocalStorage.getInstance().state.feedbackNotificationShown = true
  }
}