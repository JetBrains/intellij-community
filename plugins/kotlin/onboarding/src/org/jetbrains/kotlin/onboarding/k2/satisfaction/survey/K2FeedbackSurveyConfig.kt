// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import org.jetbrains.kotlin.onboarding.FeedbackBundle

class K2FeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

    override val surveyId: String = "k2_feedback"
    override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, 3, 18)
    override val requireIdeEAP: Boolean = true

    private val suitableIdeVersion = "2024.1"

    override fun checkIdeIsSuitable(): Boolean {
        return PlatformUtils.isIdeaUltimate() || PlatformUtils.isIdeaCommunity()
    }

    override fun checkExtraConditionSatisfied(project: Project): Boolean {
        return suitableIdeVersion == ApplicationInfo.getInstance().shortVersion &&
                K2UserTracker.getInstance().shouldShowK2FeedbackDialog()
    }

    override fun updateStateAfterDialogClosedOk(project: Project) {
        K2UserTracker.getInstance().state.userSawSurvey = true
    }

    override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialogWithEmail<out SystemDataJsonSerializable> {
        return K2FeedbackDialog(project, forTest)
    }

    override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
        return RequestFeedbackNotification(
            "Feedback In IDE",
            FeedbackBundle.message("notification.k2.satisfaction.request.title"),
            FeedbackBundle.message("notification.k2.satisfaction.request.content")
        )
    }

    override fun updateStateAfterNotificationShowed(project: Project) {
        // do nothing
    }
}