// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.gradle

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.configuration.getGradleKotlinVersion
import org.jetbrains.kotlin.onboarding.FeedbackBundle
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil
import kotlin.io.path.div

@Serializable
internal data class BuildProcessSatisfactionDialogData(
    @NlsSafe val gradleVersion: String,
    @NlsSafe val kotlinVersion: String,
    val groovyBuildFileCount: Int,
    val ktsBuildFileCount: Int,
    val commonData: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
    override fun serializeToJson(json: Json): JsonElement = json.encodeToJsonElement(this)
}

internal class BuildProcessSatisfactionDialog(
    private val project: Project,
    forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<BuildProcessSatisfactionDialogData>(project, forTest) {
    /** Increase the additional number when feedback format is changed */
    override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

    private val distributionUrlRegex = Regex("""://services\.gradle\.org/distributions/gradle-([\w.\-_]+)-(?:all|bin)\.zip""")
    private fun getGradleWrapperVersion(): GradleVersion? {
        val wrapperConfiguration = GradleUtil.getWrapperConfiguration(project.basePath)?.distribution ?: return null
        val matchResult = distributionUrlRegex.find(wrapperConfiguration.toString()) ?: return null
        val versionPart = matchResult.groupValues[1]
        return GradleVersion.version(versionPart)
    }

    private fun getGradleVersion(): GradleVersion? {
        val settings = GradleSettings.getInstance(project)
        val linkedVersion = settings.linkedProjectsSettings
            .mapNotNull { GradleInstallationManager.guessGradleVersion(it) }
            .maxOrNull()

        return linkedVersion ?: getGradleWrapperVersion()
    }

    private fun getKotlinVersions(): List<String> {
        return project.modules.mapNotNull { it.getGradleKotlinVersion() }.distinct()
    }

    private fun collectData(): BuildProcessSatisfactionDialogData {
        val allExternalModulePaths = project.modules.mapNotNullTo(mutableSetOf()) {
            ExternalSystemApiUtil.getExternalProjectPath(it)?.toNioPathOrNull()
        }
        fun countExistingFiles(filename: String): Int {
            return allExternalModulePaths.count { path ->
                VirtualFileManager.getInstance().findFileByNioPath(path / filename) != null
            }
        }
        val groovyCount = countExistingFiles("build.gradle")
        val ktsCount = countExistingFiles("build.gradle.kts")
        val gradleVersion = getGradleVersion()?.version ?: "UNKNOWN"
        val kotlinVersion = getKotlinVersions().maxOrNull() ?: "UNKNOWN"

        return BuildProcessSatisfactionDialogData(
            gradleVersion,
            kotlinVersion,
            groovyCount,
            ktsCount,
            CommonFeedbackSystemData.getCurrentData()
        )
    }

    override val mySystemInfoData: BuildProcessSatisfactionDialogData by lazy {
        collectData()
    }

    override val zendeskTicketTitle: String = "Kotlin Build Process in-IDE Feedback"
    override val zendeskFeedbackType: String = "Kotlin Build Process Feedback"
    override val myFeedbackReportId: String = "kotlin_gradle_build_process_feedback"
    override fun shouldAutoCloseZendeskTicket(): Boolean = false

    override val myShowFeedbackSystemInfoDialog: () -> Unit = {
        showFeedbackSystemInfoDialog(myProject, mySystemInfoData.commonData) {
            row(FeedbackBundle.message("build.process.info.gradle.version")) {
                label(mySystemInfoData.gradleVersion)
            }
            row(FeedbackBundle.message("build.process.info.kotlin.version")) {
                label(mySystemInfoData.kotlinVersion)
            }
            row(FeedbackBundle.message("build.process.info.groovy.build.file.count")) {
                label(mySystemInfoData.groovyBuildFileCount.toString())
            }
            row(FeedbackBundle.message("build.process.info.kts.build.file.count")) {
                label(mySystemInfoData.ktsBuildFileCount.toString())
            }
        }
    }
    override val myTitle: String = FeedbackBundle.message("dialog.build.process.gradle.satisfaction.top.title")

    override val myBlocks: List<FeedbackBlock> = listOf(
        TopLabelBlock(FeedbackBundle.message("dialog.build.process.gradle.satisfaction.title")),
        DescriptionBlock(FeedbackBundle.message("dialog.build.process.gradle.satisfaction.description")),
        RatingBlock(
            FeedbackBundle.message("dialog.build.process.gradle.satisfaction.rating.label"),
            "rating"
        ),
        TextAreaBlock(
            FeedbackBundle.message("dialog.build.process.gradle.satisfaction.improve.label"),
            "improvements"
        )
    )

    init {
        init()
    }

    override fun showThanksNotification() {
        ThanksForFeedbackNotification(
            description = FeedbackBundle.message(
                "dialog.build.process.gradle.satisfaction.feedback.content"
            )
        ).notify(myProject)
    }
}