// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
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

      val moduleRootManager = ModuleRootManager.getInstance(module) ?: return false
      val moduleJdk = moduleRootManager.sdk
      val moduleProjectLanguageLevel = moduleJdk?.let { LanguageLevel.parse(it.versionString) } ?: return false
      val sourceLanguageLevel = getLanguageLevelFromLog(logLine) ?: return false

      messageConsumer.accept(
        BuildIssueEventImpl(parentId,
                            getBuildIssue(sourceLanguageLevel, moduleProjectLanguageLevel, logLine, moduleRootManager),
                            MessageEvent.Kind.ERROR)
      )
      return true
    }

    return false
  }

  private fun getLanguageLevelFromLog(logLine: String): LanguageLevel? {
    return logLine.split(" ").last().let { LanguageLevel.parse(it) }
  }

  private fun getBuildIssue(sourceLanguageLevel: LanguageLevel,
                            moduleProjectLanguageLevel: LanguageLevel,
                            errorMessage: String,
                            moduleRootManager: ModuleRootManager): BuildIssue {
    val moduleName = moduleRootManager.module.name
    val setupModuleSdkQuickFix = SetupModuleSdkQuickFix(moduleName, moduleRootManager.isSdkInherited)
    val quickFixes = listOf(setupModuleSdkQuickFix)
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    issueDescription.append(message("maven.quickfix.source.version.great", moduleName,
                                    moduleProjectLanguageLevel.toJavaVersion(), sourceLanguageLevel.toJavaVersion(),
                                    setupModuleSdkQuickFix.id))

    return object : BuildIssue {
      override val title: String = errorMessage
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}
