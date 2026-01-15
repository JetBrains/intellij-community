// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.maven

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
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.div

private const val KOTLIN_MAVEN_PLUGIN_GROUP_ID = "org.jetbrains.kotlin"
private const val KOTLIN_MAVEN_PLUGIN_ARTIFACT_ID = "kotlin-maven-plugin"

@Serializable
internal data class MavenBuildProcessSatisfactionDialogData(
  @NlsSafe val mavenVersion: String,
  @NlsSafe val kotlinVersion: String,
  val pomFileCount: Int,
  val daysOfIdeaUsage: Int,
  val daysOfKotlinUsage: Int,
  val daysOfKotlinWithMavenUsage: Int,
  val daysOfMavenUsage: Int,
  val commonData: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement = json.encodeToJsonElement(this)
}

internal class MavenBuildProcessSatisfactionDialog(
  private val project: Project,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<MavenBuildProcessSatisfactionDialogData>(project, forTest) {
  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  private fun getMavenVersion(): String? {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val firstMavenProject = mavenProjectsManager.projects.firstOrNull() ?: return null
    val pomPath = firstMavenProject.file.parent?.path ?: return null
    return try {
      MavenDistributionsCache.getInstance(project).getMavenDistribution(pomPath).version
    } catch (_: Exception) {
      null
    }
  }

  private fun getKotlinVersions(): List<String> {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.projects
      .mapNotNull { mavenProject ->
        mavenProject.findPlugin(KOTLIN_MAVEN_PLUGIN_GROUP_ID, KOTLIN_MAVEN_PLUGIN_ARTIFACT_ID)?.version
      }
      .distinct()
  }

  private fun LocalDate.daysSinceDate(): Int {
    return ChronoUnit.DAYS.between(this, LocalDate.now()).toInt()
  }

  private fun collectData(): MavenBuildProcessSatisfactionDialogData {
    val allExternalModulePaths = project.modules.mapNotNullTo(mutableSetOf()) {
      ExternalSystemApiUtil.getExternalProjectPath(it)?.toNioPathOrNull()
    }

    fun countExistingFiles(filename: String): Int {
      return allExternalModulePaths.count { path ->
        VirtualFileManager.getInstance().findFileByNioPath(path / filename) != null
      }
    }

    val pomCount = countExistingFiles("pom.xml")
    val mavenVersion = getMavenVersion() ?: "UNKNOWN"
    val kotlinVersion = getKotlinVersions().maxOrNull() ?: "UNKNOWN"

    val daysOfIdeaUsage = KotlinNewUserTracker.getInstance().getInstallationDate()?.daysSinceDate() ?: 0
    val daysOfKotlinUsage = KotlinNewUserTracker.getInstance().getFirstKotlinUsageDate()?.daysSinceDate() ?: 0
    val daysOfKotlinWithMavenUsage = MavenBuildProcessSatisfactionSurveyStore.getInstance().getFirstKotlinMavenUsageDate()?.daysSinceDate() ?: 0
    val daysOfMavenUsage = MavenBuildProcessSatisfactionSurveyStore.getInstance().getFirstMavenUsageDate()?.daysSinceDate() ?: 0

    return MavenBuildProcessSatisfactionDialogData(
      mavenVersion = mavenVersion,
      kotlinVersion = kotlinVersion,
      pomFileCount = pomCount,
      daysOfIdeaUsage = daysOfIdeaUsage,
      daysOfKotlinUsage = daysOfKotlinUsage,
      daysOfMavenUsage = daysOfMavenUsage,
      daysOfKotlinWithMavenUsage = daysOfKotlinWithMavenUsage,
      commonData = CommonFeedbackSystemData.getCurrentData(),
    )
  }

  override suspend fun computeSystemInfoData(): MavenBuildProcessSatisfactionDialogData =
    withContext(Dispatchers.EDT) {
      collectData()
    }

  override val zendeskTicketTitle: String = "Kotlin Maven Build Process in-IDE Feedback"
  override val zendeskFeedbackType: String = "Kotlin Maven Build Process Feedback"
  override val myFeedbackReportId: String = "kotlin_maven_build_process_feedback"
  override fun shouldAutoCloseZendeskTicket(): Boolean = false

  override fun showFeedbackSystemInfoDialog(systemInfoData: MavenBuildProcessSatisfactionDialogData) {
    showFeedbackSystemInfoDialog(myProject, systemInfoData.commonData) {
      row(MavenFeedbackBundle.message("build.process.info.maven.version")) {
        label(systemInfoData.mavenVersion)
      }
      row(MavenFeedbackBundle.message("build.process.info.kotlin.version")) {
        label(systemInfoData.kotlinVersion)
      }
      row(MavenFeedbackBundle.message("build.process.info.pom.file.count")) {
        label(systemInfoData.pomFileCount.toString())
      }
      row(MavenFeedbackBundle.message("build.process.info.days.of.kotlin.usage")) {
        label(systemInfoData.daysOfKotlinUsage.toString())
      }
      row(MavenFeedbackBundle.message("build.process.info.days.of.idea.usage")) {
        label(systemInfoData.daysOfIdeaUsage.toString())
      }
      row(MavenFeedbackBundle.message("build.process.info.days.of.maven.usage")) {
        label(systemInfoData.daysOfMavenUsage.toString())
      }
      row(MavenFeedbackBundle.message("build.process.info.days.of.kotlin.maven.usage")) {
        label(systemInfoData.daysOfKotlinWithMavenUsage.toString())
      }
    }
  }

  override val myTitle: String = MavenFeedbackBundle.message("dialog.build.process.maven.satisfaction.top.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(MavenFeedbackBundle.message("dialog.build.process.maven.satisfaction.title")),
    DescriptionBlock(MavenFeedbackBundle.message("dialog.build.process.maven.satisfaction.description")),
    SegmentedButtonBlock(
      MavenFeedbackBundle.message("dialog.build.process.maven.satisfaction.rating.label", ApplicationInfo.getInstance().versionName),
      List(5) { (it + 1).toString() },
      "csat_rating",
      listOf(
        AllIcons.Survey.VeryDissatisfied,
        AllIcons.Survey.Dissatisfied,
        AllIcons.Survey.Neutral,
        AllIcons.Survey.Satisfied,
        AllIcons.Survey.VerySatisfied
      )
    )
      .addLeftBottomLabel(MavenFeedbackBundle.message("dialog.rating.leftHint"))
      .addMiddleBottomLabel(MavenFeedbackBundle.message("dialog.rating.middleHint"))
      .addRightBottomLabel(MavenFeedbackBundle.message("dialog.rating.rightHint")),
    TextAreaBlock(
      MavenFeedbackBundle.message("dialog.build.process.maven.satisfaction.improve.label"),
      "improvements"
    )
  )

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(
      description = MavenFeedbackBundle.message(
        "dialog.build.process.maven.satisfaction.feedback.content"
      )
    ).notify(myProject)
  }
}
