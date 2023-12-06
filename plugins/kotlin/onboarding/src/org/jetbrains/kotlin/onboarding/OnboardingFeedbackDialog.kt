// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification

class OnboardingFeedbackDialog(
    project: Project?,
    forTest: Boolean
) : BlockBasedFeedbackDialog<CommonFeedbackSystemData>(project, forTest) {

    /** Increase the additional number when feedback format is changed */
    override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
    override val myFeedbackReportId: String = "kotlin_onboarding"

    override val mySystemInfoData: CommonFeedbackSystemData by lazy {
        CommonFeedbackSystemData.getCurrentData()
    }

    override val myShowFeedbackSystemInfoDialog: () -> Unit = {
        showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
    }

    override val myTitle: String = FeedbackBundle.message("dialog.top.title")
    override val myBlocks: List<FeedbackBlock> = listOf(
        TopLabelBlock(FeedbackBundle.message("dialog.title")),
        DescriptionBlock(FeedbackBundle.message("dialog.description")),
        RatingBlock(
            FeedbackBundle.message("dialog.satisfaction.rating.label"),
            "satisfaction_rating"
        ),
        ComboBoxBlock(
            FeedbackBundle.message("dialog.previous.language.label"),
            List(13) { FeedbackBundle.message("dialog.previous.language.${it + 1}") },
            "previous_language"
        ),
        ComboBoxBlock(
            FeedbackBundle.message("dialog.previous.ide.label"),
            List(6) { FeedbackBundle.message("dialog.previous.ide.${it + 1}") },
            "previous_ide"
        ),
        TextAreaBlock(
            FeedbackBundle.message("dialog.suggestion.to.improve.label"),
            "suggestion_to_improve"
        )
    )

    init {
        init()
    }

    override fun showThanksNotification() {
        ThanksForFeedbackNotification(
            description = FeedbackBundle.message(
                "notification.thanks.feedback.content"
            )
        ).notify(myProject)
    }
}