// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class OnboardingFeedbackDialog(
    project: Project?,
    forTest: Boolean
) : BlockBasedFeedbackDialog<OnboardingFeedbackDialog.OnboardingFeedbackSystemData>(project, forTest) {

    /** Increase the additional number when feedback format is changed */
    override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
    override val myFeedbackReportId: String = "kotlin_onboarding"

    override suspend fun computeSystemInfoData(): OnboardingFeedbackSystemData =
        withContext(Dispatchers.EDT) { // accesses mutable state that's modified on the EDT
            val kotlinTracker = KotlinNewUserTracker.getInstance()
            OnboardingFeedbackSystemData(
                kotlinTracker.isNewKtUser(),
                kotlinTracker.isNewIdeaUser(),
                CommonFeedbackSystemData.getCurrentData()
            )
        }

    override fun showFeedbackSystemInfoDialog(systemInfoData: OnboardingFeedbackSystemData) {
        showNewUIFeedbackSystemInfoDialog(myProject, systemInfoData)
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


    @Serializable
    data class OnboardingFeedbackSystemData(
        val isNewKotlinUser: Boolean,
        val isNewIdeaUser: Boolean,
        val commonSystemInfo: CommonFeedbackSystemData
    ) : SystemDataJsonSerializable {
        override fun toString(): String {
            return buildString {
                appendLine(FeedbackBundle.message("dialog.system.info.isNewKotlinUser"))
                appendLine()
                appendLine(if (isNewKotlinUser) "True" else "False")
                appendLine()
                appendLine(FeedbackBundle.message("dialog.system.info.isNewIdeUser"))
                appendLine()
                appendLine(if (isNewIdeaUser) "True" else "False")
                appendLine()
                commonSystemInfo.toString()
            }
        }

        override fun serializeToJson(json: Json): JsonElement {
            return json.encodeToJsonElement(this)
        }
    }

    private fun showNewUIFeedbackSystemInfoDialog(
        project: Project?,
        systemInfoData: OnboardingFeedbackSystemData
    ) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
        row(FeedbackBundle.message("dialog.system.info.isNewKotlinUser")) {
            label(if (systemInfoData.isNewKotlinUser) "True" else "False") //NON-NLS
        }
        row(FeedbackBundle.message("dialog.system.info.isNewIdeUser")) {
            label(if (systemInfoData.isNewIdeaUser) "True" else "False") //NON-NLS
        }
    }
}