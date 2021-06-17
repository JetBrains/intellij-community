// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsInfo
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsInfo.CompletionTableInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.execution.MavenCommandLineOptions
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import javax.swing.Icon

class MavenTasksAndArgumentsInfo(
  project: Project,
  projectPathField: ExternalSystemProjectPathField
) : ExternalSystemTasksAndArgumentsInfo {
  override val hint: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.hint")
  override val title: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.title")
  override val tooltip: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.tooltip")
  override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.empty.state")
  override val name: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.name")

  override val tablesInfo: List<CompletionTableInfo> =
    listOf(PhasesAndGoalsCompletionTableInfo(project, projectPathField), ArgumentsCompletionTableInfo())

  private class PhasesAndGoalsCompletionTableInfo(
    project: Project,
    projectPathField: ExternalSystemProjectPathField
  ) : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.tasks.empty.text")
    override val dataIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.task.column")
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.tasks.and.arguments.description.column")

    private val phases: List<TextCompletionInfo> =
      MavenConstants.BASIC_PHASES.map { TextCompletionInfo(it) }

    private val goals: List<TextCompletionInfo> by lazy {
      val projectsManager = MavenProjectsManager.getInstance(project)
      val localFileSystem = LocalFileSystem.getInstance()
      val projectPath = projectPathField.projectPath
      val projectDir = localFileSystem.refreshAndFindFileByPath(projectPath) ?: return@lazy emptyList()
      val mavenProject = projectsManager.findContainingProject(projectDir) ?: return@lazy emptyList()
      val localRepository = projectsManager.localRepository
      mavenProject.declaredPlugins
        .mapNotNull { MavenArtifactUtil.readPluginInfo(localRepository, it.mavenId) }
        .flatMap { it.mojos }
        .map { TextCompletionInfo(it.displayName) }
    }

    override val completionInfo: List<TextCompletionInfo> by lazy {
      phases.sortedBy { it.text } + goals.sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionInfo> = emptyList()
  }

  private class ArgumentsCompletionTableInfo : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.arguments.empty.text")
    override val dataIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.tasks.and.arguments.argument.column")
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.tasks.and.arguments.description.column")

    private val options: Collection<MavenCommandLineOptions.Option> =
      MavenCommandLineOptions.getAllOptions()

    override val completionInfo: List<TextCompletionInfo> by lazy {
      options
        .map { TextCompletionInfo(it.getName(false), it.description) }
        .sortedBy { it.text } +
      options
        .map { TextCompletionInfo(it.getName(true), it.description) }
        .sortedBy { it.text }
    }

    override val tableCompletionInfo: List<TextCompletionInfo> by lazy {
      completionInfo.filter { it.text.startsWith("--") }
    }
  }
}