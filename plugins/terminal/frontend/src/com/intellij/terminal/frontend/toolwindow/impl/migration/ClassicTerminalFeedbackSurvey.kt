// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ClassicTerminalMigration
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackMoment
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils.getFeedbackMoment

/**
 * Shows a request to leave feedback when the user switches back to the Classic Terminal
 * after having been force-switched from Classic to Reworked earlier.
 */
@ApiStatus.Internal
fun askForFeedbackIfSwitchedBackToClassicTerminal(project: Project, oldEngine: TerminalEngine, newEngine: TerminalEngine) {
  ApplicationManager.getApplication().invokeLater(
    {
      if (oldEngine == TerminalEngine.REWORKED && newEngine == TerminalEngine.CLASSIC
          && ClassicTerminalMigration.wasSwitchedFromClassicTerminal()) {
        TerminalFeedbackUtils.showFeedbackNotificationOnDemand(project, ClassicTerminalFeedbackSurvey::class)
      }
    },
    ModalityState.nonModal(), // when invoked from the settings dialog, show the notification after the dialog is closed
    project.disposed,
  )
}

internal class ClassicTerminalFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(ClassicTerminalSurveyConfig)
}

private object ClassicTerminalSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String
    get() = "classic_terminal_switch_back"

  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, 12, 31)

  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return ClassicTerminalFeedbackDialog(project, forTest)
  }

  /**
   * Only show as a direct reaction to switching back to Classic (see [askForFeedbackIfSwitchedBackToClassicTerminal]),
   * never ambiently, e.g., from the idle feedback notifier.
   */
  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return getFeedbackMoment(project) == TerminalFeedbackMoment.ON_DEMAND &&
           ClassicTerminalMigration.shouldShowSwitchBackFeedbackNotification()
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification("Feedback In IDE",
                                       TerminalBundle.message("classic.switch.back.feedback.notification.title"),
                                       TerminalBundle.message("classic.switch.back.feedback.notification.text"))
  }

  override fun getRespondNotificationActionLabel(): String {
    return TerminalBundle.message("classic.switch.back.feedback.notification.action.respond")
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    ClassicTerminalMigration.setSwitchBackFeedbackNotificationShown()
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {}
}