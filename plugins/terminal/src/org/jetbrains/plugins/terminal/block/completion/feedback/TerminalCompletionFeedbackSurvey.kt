package org.jetbrains.plugins.terminal.block.completion.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackMoment
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils.getFeedbackMoment
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage

internal class TerminalCompletionFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(TerminalCompletionSurveyConfig())
}

private class TerminalCompletionSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "terminal_command_completion"

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, 11, 18) // Estimated 2025.3 release date

  override val requireIdeEAP: Boolean = true

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val usageStorageState = TerminalUsageLocalStorage.getInstance().state
    if (usageStorageState.completionFeedbackNotificationShown) {
      return false
    }
    return usageStorageState.completionPopupShownTimes >= 10 && usageStorageState.completionItemChosenTimes > 0
           || usageStorageState.completionPopupShownTimes > 0 && getFeedbackMoment(project) == TerminalFeedbackMoment.ON_DEMAND
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return TerminalCompletionFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {}

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      TerminalBundle.message("feedback.notification.title"),
      TerminalBundle.message("completion.feedback.notification.text")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    TerminalUsageLocalStorage.getInstance().recordCompletionFeedbackNotificationShown()
  }
}