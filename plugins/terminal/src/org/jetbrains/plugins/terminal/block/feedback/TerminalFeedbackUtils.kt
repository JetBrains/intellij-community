// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.feedback

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.impl.OnDemandFeedbackResolver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackMoment.AFTER_USAGE
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackMoment.ON_DEMAND
import org.jetbrains.plugins.terminal.block.feedback.TerminalFeedbackUtils.getFeedbackMoment
import kotlin.reflect.KClass

@ApiStatus.Internal
object TerminalFeedbackUtils {
  /** Used to indicate that we are trying to show the feedback notification after the feature was disabled */
  private val TERMINAL_FEEDBACK_MOMENT: Key<TerminalFeedbackMoment> = Key.create("TerminalFeedbackMoment")

  /**
   * Tries to show the feedback notification of the provided [surveyClass].
   * But makes [getFeedbackMoment] return [TerminalFeedbackMoment.ON_DEMAND]
   * during the checks in [com.intellij.platform.feedback.FeedbackSurveyConfig.checkExtraConditionSatisfied].
   * Allowing using different conditions when notification is called on demand and lazily.
   */
  fun <T : FeedbackSurvey> showFeedbackNotificationOnDemand(project: Project, surveyClass: KClass<T>) {
    project.putUserData(TERMINAL_FEEDBACK_MOMENT, ON_DEMAND)
    OnDemandFeedbackResolver.getInstance().showFeedbackNotification(surveyClass, project) { isNotificationShown: Boolean ->
      if (!isNotificationShown) {
        // If the notification was not shown, the TERMINAL_FEEDBACK_MOMENT would not be used, so we can just clear it.
        project.putUserData(TERMINAL_FEEDBACK_MOMENT, null)
      }
    }
  }

  fun getFeedbackMoment(project: Project): TerminalFeedbackMoment {
    return project.getUserData(TERMINAL_FEEDBACK_MOMENT) ?: AFTER_USAGE
  }
}

@ApiStatus.Internal
enum class TerminalFeedbackMoment {
  ON_DEMAND, AFTER_USAGE
}