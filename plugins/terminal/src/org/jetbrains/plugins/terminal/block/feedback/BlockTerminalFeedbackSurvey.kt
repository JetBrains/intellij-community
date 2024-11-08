// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.feedback

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
import org.jetbrains.plugins.terminal.block.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackEvent
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment.AFTER_USAGE
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment.ON_DISABLING
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector

/** Used to indicate that we are trying to show the feedback notification after block terminal is disabled */
private val BLOCK_TERMINAL_DISABLING: Key<Boolean> = Key.create("BlockTerminalDisabling")

internal fun showBlockTerminalFeedbackNotification(project: Project) {
  // BLOCK_TERMINAL_DISABLING can be used in showFeedbackNotification and after exiting this method.
  // This key will be left in the project user data, and won't be cleared if the feedback notification is shown.
  project.putUserData(BLOCK_TERMINAL_DISABLING, true)
  OnDemandFeedbackResolver.getInstance()
    .showFeedbackNotification(BlockTerminalFeedbackSurvey::class, project) { isNotificationShown: Boolean ->
      if (!isNotificationShown) {
        // If the notification was not shown, the BLOCK_TERMINAL_DISABLING would not be used, so we can just clear it.
        project.putUserData(BLOCK_TERMINAL_DISABLING, null)
      }
  }
}

internal fun getFeedbackMoment(project: Project): TerminalFeedbackMoment {
  return if (project.getUserData(BLOCK_TERMINAL_DISABLING) == true) ON_DISABLING else AFTER_USAGE
}

internal class BlockTerminalFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(BlockTerminalSurveyConfig())
}

internal class BlockTerminalSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "new_terminal"

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    if (!forTest) {
      TerminalUsageTriggerCollector.triggerFeedbackSurveyEvent(project, TerminalFeedbackEvent.DIALOG_SHOWN, getFeedbackMoment(project))
    }
    return BlockTerminalFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    TerminalUsageTriggerCollector.triggerFeedbackSurveyEvent(project, TerminalFeedbackEvent.FEEDBACK_SENT, getFeedbackMoment(project))
  }

  // Last date is an approximate time of 2025.1 release
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, 4, 1)

  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val usageStorage = TerminalUsageLocalStorage.getInstance()
    return !usageStorage.state.feedbackNotificationShown &&
           // show notification if user executed enough commands or if block terminal is being disabled
           (usageStorage.executedCommandsNumber >= 15 || usageStorage.executedCommandsNumber > 0 && getFeedbackMoment(project) == ON_DISABLING)
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification("Feedback In IDE",
                                       TerminalBundle.message("feedback.notification.title"),
                                       TerminalBundle.message("feedback.notification.text"))
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    TerminalUsageLocalStorage.getInstance().state.feedbackNotificationShown = true
    TerminalUsageTriggerCollector.triggerFeedbackSurveyEvent(project, TerminalFeedbackEvent.NOTIFICATION_SHOWN, getFeedbackMoment(project))
  }
}
