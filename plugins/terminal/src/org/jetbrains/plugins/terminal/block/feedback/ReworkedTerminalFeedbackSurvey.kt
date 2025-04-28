// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.feedback

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment.AFTER_USAGE
import org.jetbrains.plugins.terminal.fus.TerminalFeedbackMoment.ON_DISABLING

/** Used to indicate that we are trying to show the feedback notification after the reworked terminal is disabled */
private val REWORKED_TERMINAL_DISABLING: Key<Boolean> = Key.create("ReworkedTerminalDisabling")

@ApiStatus.Internal
fun askForFeedbackIfReworkedTerminalDisabled(project: Project, oldEngine: TerminalEngine, newEngine: TerminalEngine) {
  ApplicationManager.getApplication().invokeLater(
    {
      if (oldEngine == TerminalEngine.REWORKED && newEngine != TerminalEngine.REWORKED) {
        // REWORKED_TERMINAL_DISABLING can be used in showFeedbackNotification and after exiting this method.
        // This key will be left in the project user data, and won't be cleared if the feedback notification is shown.
        project.putUserData(REWORKED_TERMINAL_DISABLING, true)
        OnDemandFeedbackResolver.getInstance()
          .showFeedbackNotification(ReworkedTerminalFeedbackSurvey::class, project) { isNotificationShown: Boolean ->
            if (!isNotificationShown) {
              // If the notification was not shown, the REWORKED_TERMINAL_DISABLING would not be used, so we can just clear it.
              project.putUserData(REWORKED_TERMINAL_DISABLING, null)
            }
          }
      }
    },
    ModalityState.nonModal(), // when invoked from the settings dialog, show the notification after the dialog is closed
    project.disposed,
  )
}

internal fun getFeedbackMoment(project: Project): TerminalFeedbackMoment {
  return if (project.getUserData(REWORKED_TERMINAL_DISABLING) == true) ON_DISABLING else AFTER_USAGE
}

internal class ReworkedTerminalFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(ReworkedTerminalSurveyConfig())
}

internal class ReworkedTerminalSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "reworked_terminal"

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return ReworkedTerminalFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) { }

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, 7, 15)

  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val usageStorage = TerminalUsageLocalStorage.getInstance()
    // Show notification if the user has executed enough commands or if the reworked terminal is being disabled.
    return !usageStorage.state.feedbackNotificationShown &&
           (
             usageStorage.state.enterKeyPressedTimes >= 15 ||
             usageStorage.state.enterKeyPressedTimes > 0 && getFeedbackMoment(project) == ON_DISABLING
           )
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification("Feedback In IDE",
                                       TerminalBundle.message("feedback.notification.title"),
                                       TerminalBundle.message("feedback.notification.text"))
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    TerminalUsageLocalStorage.getInstance().recordFeedbackNotificationShown()
  }
}
