// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.gradle

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

class BuildProcessSatisfactionSurveyConfig : InIdeFeedbackSurveyConfig {

    override val surveyId: String = "kotlin_gradle_build_process_feedback"
    override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, 12, 1)
    override val requireIdeEAP: Boolean = false

    override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialogWithEmail<out SystemDataJsonSerializable> {
        return BuildProcessSatisfactionDialog(project, forTest)
    }

    override fun updateStateAfterDialogClosedOk(project: Project) {
        BuildProcessSatisfactionSurveyStore.getInstance().recordSurveyShown()
    }

    override fun checkIdeIsSuitable(): Boolean {
        return PlatformUtils.isIdeaUltimate() || PlatformUtils.isIdeaCommunity()
    }

    override fun checkExtraConditionSatisfied(project: Project): Boolean {
        if (!project.isKotlinGradleProject()) return false
        return BuildProcessSatisfactionSurveyStore.getInstance().shouldShowDialog()
    }

    override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
        return RequestFeedbackNotification(
            "Feedback In IDE",
            GradleFeedbackBundle.message("build.process.gradle.satisfaction.request.title"),
            GradleFeedbackBundle.message("build.process.gradle.satisfaction.request.content")
        )
    }

    override fun updateStateAfterNotificationShowed(project: Project) {
        // do nothing
    }
}