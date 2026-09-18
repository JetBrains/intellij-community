// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.terminal.block.reworked.TerminalAiInlineCompletion
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage

internal class TerminalInlineCompletionFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(TerminalInlineCompletionFeedbackSurveyConfig())
}

private class TerminalInlineCompletionFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "terminal_inline_completion"

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, 10, 20) // Estimated Code Freeze

  override val requireIdeEAP: Boolean = true

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val state = TerminalUsageLocalStorage.getInstance().state
    val usedInlineCompletionEnough = state.completionInlineShownTimes >= MIN_SHOWN_SUGGESTIONS &&
                                    state.inlineCompletionAcceptedTimes >= MIN_ACCEPTED_SUGGESTIONS
    val optedOutAfterUsingInlineCompletion = !TerminalAiInlineCompletion.isEnabled() &&
                                              state.completionInlineShownTimes >= MIN_SHOWN_SUGGESTIONS_BEFORE_OPT_OUT
    return usedInlineCompletionEnough || optedOutAfterUsingInlineCompletion
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return TerminalInlineCompletionFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback in IDE",
      TerminalBundle.message("inline.completion.feedback.notification.title"),
      TerminalBundle.message("inline.completion.feedback.notification.text")
    )
  }

  override fun getRespondNotificationActionLabel(): String {
    return TerminalBundle.message("inline.completion.feedback.notification.btn")
  }

  override fun getCancelNotificationActionLabel(): String {
    return TerminalBundle.message("inline.completion.feedback.notification.link")
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }

  private companion object {
    const val MIN_SHOWN_SUGGESTIONS: Int = 10
    const val MIN_ACCEPTED_SUGGESTIONS: Int = 3
    const val MIN_SHOWN_SUGGESTIONS_BEFORE_OPT_OUT: Int = 5
  }
}