// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.compiler.progress.BuildIssueContributor
import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.CompletableFuture

class JpsLanguageLevelQuickFix : BuildIssueContributor {
  private val matchersList = listOf(
    listOf("source release", "requires target release"),
    listOf("release version", "not supported"),
    listOf("invalid source release:")
  )

  override fun createBuildIssue(project: Project,
                                moduleNames: Collection<String>,
                                title: String,
                                message: String,
                                kind: MessageEvent.Kind,
                                virtualFile: VirtualFile?,
                                navigatable: Navigatable?): BuildIssue? {
    val manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject) return null

    if (moduleNames.size != 1) {
      return null
    }
    val moduleName = moduleNames.first()
    val failedId = ModuleManager.getInstance(project).findModuleByName(moduleName)
                     ?.let { MavenProjectsManager.getInstance(project).findProject(it) }?.mavenId ?: return null
    val mavenProject = manager.findProject(failedId) ?: return null
    val moduleJdk = MavenUtil.getModuleJdk(manager, mavenProject)
    val moduleProjectLanguageLevel = moduleJdk?.let { LanguageLevel.parse(it.versionString) } ?: return null

    val sourceLanguageLevel = getLanguageLevelFromError(message) ?: return null
    if (sourceLanguageLevel.isLessThan(moduleProjectLanguageLevel)) {
      return SourceLevelBuildIssue(title, message, mavenProject, moduleJdk)
    }
    else {
      return getBuildIssue(sourceLanguageLevel, moduleProjectLanguageLevel, message, mavenProject, moduleName)
    }
  }

  fun getLanguageLevelFromError(message: String): LanguageLevel? {
    val targetMessage = matchersList
                          .filter { it.all { message.contains(it) } }
                          .map { message.substring(message.indexOf(it.first())) }
                          .firstOrNull() ?: return null
    return targetMessage.replace("[^.0123456789]".toRegex(), " ")
      .trim().split(" ")
      .firstOrNull()?.let { LanguageLevel.parse(it) }
  }

  private fun getBuildIssue(sourceLanguageLevel: LanguageLevel,
                            moduleProjectLanguageLevel: LanguageLevel,
                            errorMessage: String,
                            mavenProject: MavenProject,
                            moduleName: String): BuildIssue {
    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\nModule $moduleName project JDK ${moduleProjectLanguageLevel.toJavaVersion()} " +
                            "is lower then source version ${sourceLanguageLevel.toJavaVersion()}. \nPossible solution:\n")
    val setupModuleSdkQuickFix = SetupModuleSdkQuickFix(moduleName)
    quickFixes.add(setupModuleSdkQuickFix)
    issueDescription.append(
      " - Upgrade module JDK in project settings to ${sourceLanguageLevel.toJavaVersion()} or higher." +
      " <a href=\"${setupModuleSdkQuickFix.id}\">Open project settings</a>.\n")
    val updateSourceLevelQuickFix = UpdateSourceLevelQuickFix(mavenProject)
    quickFixes.add(updateSourceLevelQuickFix)
    issueDescription.append(
      " - Downgrade JDK version in source to ${moduleProjectLanguageLevel.toJavaVersion()} (Not recommended)." +
      " <a href=\"${updateSourceLevelQuickFix.id}\">Update pom.xml</a>.\n")

    return object : BuildIssue {
      override val title: String = errorMessage
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

class SetupModuleSdkQuickFix(val moduleName: String) : BuildIssueQuickFix {

  override val id: String = "setup_module_sdk_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    ProjectSettingsService.getInstance(project)
      .showModuleConfigurationDialog(moduleName, JavaUiBundle.message("module.libraries.target.jdk.module.radio"))
    return CompletableFuture.completedFuture<Any>(null)
  }
}