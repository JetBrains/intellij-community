// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.gradle

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleJava.kotlinGradlePluginVersion
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.div

@Serializable
internal data class BuildProcessSatisfactionDialogData(
    @NlsSafe val gradleVersion: String,
    @NlsSafe val kotlinVersion: String,
    val groovyBuildFileCount: Int,
    val ktsBuildFileCount: Int,
    val daysOfIdeaUsage: Int,
    val daysOfKotlinUsage: Int,
    val daysOfKotlinWithGradleUsage: Int,
    val daysOfGradleUsage: Int,
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
        return project.modules.mapNotNull { it.kotlinGradlePluginVersion?.versionString }.distinct()
    }

    private fun LocalDate.daysSinceDate(): Int {
        return ChronoUnit.DAYS.between(this, LocalDate.now()).toInt()
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

        val daysOfIdeaUsage = KotlinNewUserTracker.getInstance().getInstallationDate()?.daysSinceDate() ?: 0
        val daysOfKotlinUsage = KotlinNewUserTracker.getInstance().getFirstKotlinUsageDate()?.daysSinceDate() ?: 0
        val daysOfKotlinWithGradleUsage = BuildProcessSatisfactionSurveyStore.getInstance().getFirstKotlinGradleUsageDate()?.daysSinceDate() ?: 0
        val daysOfGradleUsage = BuildProcessSatisfactionSurveyStore.getInstance().getFirstGradleUsageDate()?.daysSinceDate() ?: 0

        return BuildProcessSatisfactionDialogData(
            gradleVersion = gradleVersion,
            kotlinVersion = kotlinVersion,
            groovyBuildFileCount = groovyCount,
            ktsBuildFileCount = ktsCount,
            daysOfIdeaUsage = daysOfIdeaUsage,
            daysOfKotlinUsage = daysOfKotlinUsage,
            daysOfGradleUsage = daysOfGradleUsage,
            daysOfKotlinWithGradleUsage = daysOfKotlinWithGradleUsage,
            commonData = CommonFeedbackSystemData.getCurrentData(),
        )
    }

    override suspend fun computeSystemInfoData(): BuildProcessSatisfactionDialogData =
        withContext(Dispatchers.EDT) { // collectData is rather complicated, and may need WIL and/or EDT
            collectData()
        }

    override val zendeskTicketTitle: String = "Kotlin Build Process in-IDE Feedback"
    override val zendeskFeedbackType: String = "Kotlin Build Process Feedback"
    override val myFeedbackReportId: String = "kotlin_gradle_build_process_feedback"
    override fun shouldAutoCloseZendeskTicket(): Boolean = false

    override fun showFeedbackSystemInfoDialog(systemInfoData: BuildProcessSatisfactionDialogData) {
        showFeedbackSystemInfoDialog(myProject, systemInfoData.commonData) {
            row(GradleFeedbackBundle.message("build.process.info.gradle.version")) {
                label(systemInfoData.gradleVersion)
            }
            row(GradleFeedbackBundle.message("build.process.info.kotlin.version")) {
                label(systemInfoData.kotlinVersion)
            }
            row(GradleFeedbackBundle.message("build.process.info.groovy.build.file.count")) {
                label(systemInfoData.groovyBuildFileCount.toString())
            }
            row(GradleFeedbackBundle.message("build.process.info.kts.build.file.count")) {
                label(systemInfoData.ktsBuildFileCount.toString())
            }
            row(GradleFeedbackBundle.message("build.process.info.days.of.kotlin.usage")) {
                label(systemInfoData.daysOfKotlinUsage.toString())
            }
            row(GradleFeedbackBundle.message("build.process.info.days.of.idea.usage")) {
                label(systemInfoData.daysOfIdeaUsage.toString())
            }
            row(GradleFeedbackBundle.message("build.process.info.days.of.gradle.usage")) {
                label(systemInfoData.daysOfGradleUsage.toString())
            }
            row(GradleFeedbackBundle.message("build.process.info.days.of.kotlin.gradle.usage")) {
                label(systemInfoData.daysOfKotlinWithGradleUsage.toString())
            }
        }
    }

    override val myTitle: String = GradleFeedbackBundle.message("dialog.build.process.gradle.satisfaction.top.title")

    override val myBlocks: List<FeedbackBlock> = listOf(
        TopLabelBlock(GradleFeedbackBundle.message("dialog.build.process.gradle.satisfaction.title")),
        DescriptionBlock(GradleFeedbackBundle.message("dialog.build.process.gradle.satisfaction.description")),
        SegmentedButtonBlock(GradleFeedbackBundle.message("dialog.build.process.gradle.satisfaction.rating.label",ApplicationInfo.getInstance().versionName),
                             List(5) { (it + 1).toString() },
                             "csat_rating",
                             listOf(
                                 AllIcons.Survey.VeryDissatisfied,
                                 AllIcons.Survey.Dissatisfied,
                                 AllIcons.Survey.Neutral,
                                 AllIcons.Survey.Satisfied,
                                 AllIcons.Survey.VerySatisfied
                             ))
            .addLeftBottomLabel(GradleFeedbackBundle.message("dialog.rating.leftHint"))
            .addMiddleBottomLabel(GradleFeedbackBundle.message("dialog.rating.middleHint"))
            .addRightBottomLabel(GradleFeedbackBundle.message("dialog.rating.rightHint")),
        TextAreaBlock(
            GradleFeedbackBundle.message("dialog.build.process.gradle.satisfaction.improve.label"),
            "improvements"
        )
    )

    init {
        init()
    }

    override fun showThanksNotification() {
        ThanksForFeedbackNotification(
            description = GradleFeedbackBundle.message(
                "dialog.build.process.gradle.satisfaction.feedback.content"
            )
        ).notify(myProject)
    }
}