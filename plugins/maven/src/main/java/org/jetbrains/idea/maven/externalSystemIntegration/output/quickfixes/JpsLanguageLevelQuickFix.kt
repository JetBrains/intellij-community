// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.compiler.progress.BuildIssueContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle.message
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.concurrent.CompletableFuture

class JpsLanguageLevelQuickFix : BuildIssueContributor {
  private val matchersList = listOf(
    listOf("source release", "requires target release"),
    listOf("release version", "not supported"),
    listOf("invalid source release:"),
    listOf("invalid target release")
  )

  override fun createBuildIssue(project: Project,
                                moduleNames: Collection<String>,
                                title: String,
                                message: String,
                                kind: MessageEvent.Kind,
                                virtualFile: VirtualFile?,
                                navigatable: Navigatable?): BuildIssue? {
    if (project.isDisposed) return null
    val mavenManager = MavenProjectsManager.getInstance(project)
    if (!mavenManager.isMavenizedProject) return null

    if (moduleNames.size != 1) {
      return null
    }
    val moduleName = moduleNames.first()
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return null
    val mavenProject = mavenManager.findProject(module) ?: return null
    val moduleRootManager = ModuleRootManager.getInstance(module) ?: return null
    val moduleJdk = moduleRootManager.sdk

    val moduleProjectLanguageLevel = moduleJdk?.let { LanguageLevel.parse(it.versionString) } ?: return null
    val sourceLanguageLevel = getLanguageLevelFromError(message) ?: return null
    if (sourceLanguageLevel.isLessThan(moduleProjectLanguageLevel)) {
      return getBuildIssueSourceVersionLess(sourceLanguageLevel, moduleProjectLanguageLevel, message, mavenProject, moduleRootManager)
    }
    else {
      return getBuildIssueSourceVersionGreat(sourceLanguageLevel, moduleProjectLanguageLevel, message, moduleRootManager)
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

  private fun getBuildIssueSourceVersionGreat(sourceLanguageLevel: LanguageLevel,
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

  private fun getBuildIssueSourceVersionLess(sourceLanguageLevel: LanguageLevel,
                                             moduleProjectLanguageLevel: LanguageLevel,
                                             errorMessage: String,
                                             mavenProject: MavenProject,
                                             moduleRootManager: ModuleRootManager): BuildIssue {
    val moduleName = moduleRootManager.module.name
    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder(errorMessage)
    issueDescription.append("\n\n")
    issueDescription.append(message("maven.quickfix.source.version.less.header", moduleName,
                                    moduleProjectLanguageLevel.toJavaVersion(), sourceLanguageLevel.toJavaVersion()))
    val setupModuleSdkQuickFix = SetupModuleSdkQuickFix(moduleName, moduleRootManager.isSdkInherited)
    quickFixes.add(setupModuleSdkQuickFix)
    issueDescription.append("\n")
    issueDescription.append(message("maven.quickfix.source.version.less.part1",
                                    sourceLanguageLevel.toJavaVersion(), setupModuleSdkQuickFix.id))
    val updateSourceLevelQuickFix = UpdateSourceLevelQuickFix(mavenProject)
    quickFixes.add(updateSourceLevelQuickFix)
    issueDescription.append("\n")
    issueDescription.append(message("maven.quickfix.source.version.less.part2",
                                    moduleProjectLanguageLevel.toJavaVersion(), updateSourceLevelQuickFix.id))

    return object : BuildIssue {
      override val title: String = errorMessage
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

class SetupModuleSdkQuickFix(val moduleName: String, val isSdkInherited: Boolean) : BuildIssueQuickFix {

  override val id: String = "setup_module_sdk_quick_fix"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    if (isSdkInherited) {
      ProjectSettingsService.getInstance(project).openProjectSettings()
    }
    else {
      ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(moduleName, ClasspathEditor.getName())
    }
    return CompletableFuture.completedFuture<Any>(null)
  }
}