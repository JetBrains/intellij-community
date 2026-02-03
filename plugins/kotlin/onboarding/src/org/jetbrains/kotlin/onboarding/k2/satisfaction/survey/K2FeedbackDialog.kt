// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.CommonBlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RatingBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import org.jetbrains.kotlin.onboarding.DescriptionBlockWithHint
import org.jetbrains.kotlin.onboarding.FeedbackBundle

class K2FeedbackDialog(
    project: Project?,
    forTest: Boolean
) : CommonBlockBasedFeedbackDialogWithEmail(project, forTest) {

    /** Increase the additional number when feedback format is changed */
    override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 2

    override val zendeskTicketTitle: String = "K2 in-IDE Feedback"
    override val zendeskFeedbackType: String = "K2 in-IDE Feedback"
    override val myFeedbackReportId: String = "k2_feedback"

    override fun shouldAutoCloseZendeskTicket(): Boolean {
        return false
    }

    override val myTitle: String = FeedbackBundle.message("dialog.k2.satisfaction.top.title")
    override val myBlocks: List<FeedbackBlock> = listOf(
        TopLabelBlock(FeedbackBundle.message("dialog.k2.satisfaction.title")),
        DescriptionBlockWithHint(
            myLabel = FeedbackBundle.message("dialog.k2.satisfaction.description"),
            myHint = FeedbackBundle.message("dialog.k2.satisfaction.description.hint")
        ),
        RatingBlock(
            FeedbackBundle.message("dialog.k2.satisfaction.performance.rating.label"),
            "performance_rating"
        ),
        RatingBlock(
            FeedbackBundle.message("dialog.k2.satisfaction.quality.rating.label"),
            "quality_rating"
        ),
        TextAreaBlock(
            FeedbackBundle.message("dialog.k2.satisfaction.biggest.quality.problems.text.label"),
            "biggest_quality_problems"
        ),
        TextAreaBlock(
            FeedbackBundle.message("dialog.k2.satisfaction.features.missed.most.text.label"),
            "most_missed_features"
        )
    )

    init {
        init()
    }

    override fun showThanksNotification() {
        ThanksForFeedbackNotification(
            description = FeedbackBundle.message(
                "notification.k2.satisfaction.thanks.feedback.content"
            )
        ).notify(myProject)
    }
}