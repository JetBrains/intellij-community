// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.ui.getActionShortcutText
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.execution.MavenCommandLineOptions
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import javax.swing.Icon

class MavenCommandLineInfo(project: Project, projectPathField: WorkingDirectoryField) : CommandLineInfo {
  override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.name")
  override val settingsHint: String = MavenConfigurableBundle.message(
    "maven.run.configuration.command.line.hint",
    getActionShortcutText(IdeActions.ACTION_CODE_COMPLETION)
  )

  override val dialogTitle: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.title")
  override val dialogTooltip: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.tooltip")

  override val fieldEmptyState: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.empty.state")

  override val tablesInfo: List<CompletionTableInfo> = listOf(
    PhasesAndGoalsCompletionTableInfo(project, projectPathField),
    ArgumentsCompletionTableInfo()
  )

  private class PhasesAndGoalsCompletionTableInfo(
    project: Project,
    workingDirectoryField: WorkingDirectoryField
  ) : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.tasks.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.task.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.command.line.description.column")

    private val phases: List<TextCompletionInfo> =
      MavenConstants.BASIC_PHASES.map { TextCompletionInfo(it) }

    private val goals: List<TextCompletionInfo> by lazy {
      val projectsManager = MavenProjectsManager.getInstance(project)
      val localFileSystem = LocalFileSystem.getInstance()
      val projectPath = workingDirectoryField.workingDirectory
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

    override val tableCompletionInfo: List<TextCompletionInfo> by lazy { completionInfo }
  }

  private class ArgumentsCompletionTableInfo : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.arguments.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.argument.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.command.line.description.column")

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