// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.feedback

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ActionBasedFeedbackConfig
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils.getFeedbackMoment
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage

@ApiStatus.Internal
fun askForFeedbackIfReworkedTerminalDisabled(project: Project, oldEngine: TerminalEngine, newEngine: TerminalEngine) {
  ApplicationManager.getApplication().invokeLater(
    {
      if (oldEngine == TerminalEngine.REWORKED && newEngine != TerminalEngine.REWORKED) {
        TerminalFeedbackUtils.showFeedbackNotificationOnDemand(project, ReworkedTerminalFeedbackSurvey::class)
      }
    },
    ModalityState.nonModal(), // when invoked from the settings dialog, show the notification after the dialog is closed
    project.disposed,
  )
}

internal class ReworkedTerminalFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(ReworkedTerminalSurveyConfig)
}

@ApiStatus.Internal
object ReworkedTerminalSurveyConfig : InIdeFeedbackSurveyConfig, ActionBasedFeedbackConfig {
  override val surveyId: String
    get() = "reworked_terminal"

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, 11, 15)

  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return ReworkedTerminalFeedbackDialog(project, forTest)
  }

  // always true because separate conditions are used for actions and for 
  override fun checkExtraConditionSatisfied(project: Project): Boolean = true

  override fun checkExtraConditionSatisfiedForNotification(project: Project): Boolean {
    val usageStorage = TerminalUsageLocalStorage.getInstance()
    // Show notification if the user has executed enough commands or if the reworked terminal is being disabled.
    return !usageStorage.state.feedbackNotificationShown &&
           (
             usageStorage.state.enterKeyPressedTimes >= 15 ||
             usageStorage.state.enterKeyPressedTimes > 0 && getFeedbackMoment(project) == TerminalFeedbackMoment.ON_DEMAND
           )
  }

  override fun checkExtraConditionSatisfiedForAction(project: Project): Boolean {
    // Explicitly sending feedback is only enabled when the reworked terminal is enabled.
    return TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification("Feedback In IDE",
                                       TerminalBundle.message("feedback.notification.title"),
                                       TerminalBundle.message("feedback.notification.text"))
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    TerminalUsageLocalStorage.getInstance().recordFeedbackNotificationShown()
  }

  override fun updateStateAfterDialogClosedOk(project: Project) { }
}
