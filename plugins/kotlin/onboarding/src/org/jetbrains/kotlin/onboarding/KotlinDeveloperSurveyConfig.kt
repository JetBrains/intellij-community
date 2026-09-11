// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

private const val SURVEY_URL = "https://surveys.jetbrains.com/s3/kds2026-ide"

/**
 * Shows the Kotlin developer survey 2026 notification to every user who has opened a Kotlin file.
 * The respond action opens the survey in the browser.
 */
internal class KotlinDeveloperSurveyConfig : ExternalFeedbackSurveyConfig {

    override val surveyId: String = "kotlin_developer_survey_2026"
    override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, 11, 20)
    override val requireIdeEAP: Boolean = false

    override fun checkIdeIsSuitable(): Boolean {
        return PlatformUtils.isIdeaUltimate()
    }

    override fun checkExtraConditionSatisfied(project: Project): Boolean {
        return KotlinNewUserTracker.getInstance().hasUsedKotlin()
    }

    override fun getUrlToSurvey(project: Project): String = SURVEY_URL

    override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
        return FeedbackNotificationWithKotlinLogo(
            "Feedback In IDE",
            FeedbackBundle.message("notification.request.title"),
            FeedbackBundle.message("notification.request.content")
        )
    }

    override fun getRespondNotificationActionLabel(): String {
        return FeedbackBundle.message("notification.request.action.take.survey")
    }

    override fun updateStateAfterNotificationShowed(project: Project) {
        // The platform records the show count.
    }

    override fun updateStateAfterRespondActionInvoked(project: Project) {
        // The platform marks the survey as answered.
    }
}
