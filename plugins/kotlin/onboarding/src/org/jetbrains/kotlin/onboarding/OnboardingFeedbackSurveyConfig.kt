// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

class OnboardingFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

    override val surveyId: String = "kotlin_onboarding"
    override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, 3, 15)
    override val requireIdeEAP: Boolean = false

    private val suitableIdeVersion = "2023.3"

    override fun checkIdeIsSuitable(): Boolean {
        return PlatformUtils.isIdeaUltimate() || PlatformUtils.isIdeaCommunity()
    }

    override fun checkExtraConditionSatisfied(project: Project): Boolean {
        return suitableIdeVersion == ApplicationInfo.getInstance().shortVersion &&
                KotlinNewUserTracker.getInstance().shouldShowNewUserDialog()
    }

    override fun updateStateAfterDialogClosedOk(project: Project) {
        // do nothing
    }

    override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
        return OnboardingFeedbackDialog(project, forTest)
    }

    override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
        return RequestFeedbackNotification(
            "Feedback In IDE",
            FeedbackBundle.message("notification.request.title"),
            FeedbackBundle.message("notification.request.content")
        )
    }

    override fun updateStateAfterNotificationShowed(project: Project) {
        // do nothing
    }
}