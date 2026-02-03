// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenRunnerSettingsQuickFix
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenSpyLoggedEventParser
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectBundle.message
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.function.Consumer

class InvalidTargetReleaseQuickFix : MavenSpyLoggedEventParser {

  override fun supportsType(type: MavenEventType) = type == MavenEventType.MOJO_FAILED

  override fun processLogLine(parentId: Any,
                              parsingContext: MavenParsingContext,
                              logLine: String,
                              messageConsumer: Consumer<in BuildEvent?>): Boolean {
    if (logLine.contains("invalid target release:")) {
      val lastErrorProject = parsingContext.startedProjects.last() + ":"
      val failedProject = parsingContext.projectsInReactor.find { it.startsWith(lastErrorProject) } ?: return false
      val project = parsingContext.ideaProject
      val mavenProject = MavenProjectsManager.getInstance(project).findProject(MavenId(failedProject)) ?: return false
      if (mavenProject.mavenId.artifactId == null) return false

      val module = ModuleManager.getInstance(project).findModuleByName(mavenProject.mavenId.artifactId!!) ?: return false
      val runnerSdkName = getRunnerSdkName(parsingContext, project)
      val requiredLanguageLevel = getLanguageLevelFromLog(logLine) ?: return false

      val persistedRunConfiguration = RunManager.getInstance(project).findConfigurationByTypeAndName(parsingContext.runConfiguration.type,
                                                                                                     parsingContext.runConfiguration.name)

      val buildIssue = if (persistedRunConfiguration == null || persistedRunConfiguration.configuration !is MavenRunConfiguration) {
        getBuildIssueForDefaultRunner(module.name, runnerSdkName, logLine,
                                      requiredLanguageLevel)
      }
      else {
        getBuildIssueForRunConfiguration(module.name, persistedRunConfiguration, runnerSdkName, logLine,
                                         requiredLanguageLevel)
      }
      messageConsumer.accept(
        BuildIssueEventImpl(parentId,
                            buildIssue,
                            MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }

  private fun getRunnerSdkName(parsingContext: MavenParsingContext,
                               project: Project): String? {
    val jreName = parsingContext.runConfiguration.runnerSettings?.jreName

    if (jreName == null) {
      return ProjectRootManager.getInstance(project).projectSdkName
    }
    return ExternalSystemJdkUtil.resolveJdkName(ProjectRootManager.getInstance(project).projectSdk, jreName)?.name
  }


  private fun getLanguageLevelFromLog(logLine: String): LanguageLevel? {
    return logLine.split(" ").last().let { LanguageLevel.parse(it) }
  }

  private fun getBuildIssueForRunConfiguration(moduleName: @NlsSafe String,
                                               persistedRunConfiguration: RunnerAndConfigurationSettings,
                                               runnerSdkName: @NlsSafe String?,
                                               errorMessage: @NlsSafe String,
                                               requiredLanguageLevel: LanguageLevel): BuildIssue {
    val setupRunConfigQuickFix = MavenRunConfigurationOpenQuickFix(persistedRunConfiguration)
    val quickFixes = listOf(setupRunConfigQuickFix)
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    if (runnerSdkName == null) {
      issueDescription.append(message("maven.quickfix.invalid.target.release.version.run.config.unknown.sdk", moduleName,
                                      requiredLanguageLevel.toJavaVersion(), persistedRunConfiguration.name, setupRunConfigQuickFix.id))
    }
    else {
      issueDescription.append(message("maven.quickfix.invalid.target.release.version.run.config", runnerSdkName, moduleName,
                                      requiredLanguageLevel.toJavaVersion(), persistedRunConfiguration.name, setupRunConfigQuickFix.id))
    }

    return buildIssue(errorMessage, issueDescription, quickFixes)
  }


  private fun getBuildIssueForDefaultRunner(moduleName: @NlsSafe String,
                                            runnerSdkName: @NlsSafe String?,
                                            errorMessage: @NlsSafe String,
                                            requiredLanguageLevel: LanguageLevel): BuildIssue {
    val setupRunnerQuickFix = OpenMavenRunnerSettingsQuickFix("JRE")
    val quickFixes = listOf(setupRunnerQuickFix)
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    if (runnerSdkName == null) {
      issueDescription.append(message("maven.quickfix.invalid.target.release.version.unknown.sdk", moduleName,
                                      requiredLanguageLevel.toJavaVersion(), setupRunnerQuickFix.id))

    }
    else {
      issueDescription.append(message("maven.quickfix.invalid.target.release.version", runnerSdkName, moduleName,
                                      requiredLanguageLevel.toJavaVersion(), setupRunnerQuickFix.id))
    }

    return buildIssue(errorMessage, issueDescription, quickFixes)
  }

  private fun buildIssue(errorMessage: @NlsSafe String,
                         issueDescription: StringBuilder,
                         quickFixes: List<BuildIssueQuickFix>): BuildIssue {
    return object : BuildIssue {
      override val title: String = errorMessage
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}
