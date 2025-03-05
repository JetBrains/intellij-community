// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.command.line.CompletionTableInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.util.createTextModificationTracker
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getActionShortcutText
import com.intellij.openapi.util.ModificationTracker
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
    private val project: Project,
    private val workingDirectoryField: WorkingDirectoryField
  ) : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.tasks.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.task.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.command.line.description.column")

    override val completionModificationTracker: ModificationTracker =
      workingDirectoryField.createTextModificationTracker()

    private fun collectPhases(): List<TextCompletionInfo> {
      return MavenConstants.BASIC_PHASES
        .map { TextCompletionInfo(it) }
        .sortedBy { it.text }
    }

    private suspend fun collectGoals(): List<TextCompletionInfo> {
      val projectDirectory = blockingContext { workingDirectoryField.getWorkingDirectoryVirtualFile() }
                             ?: return emptyList()
      val projectsManager = MavenProjectsManager.getInstance(project)
      val mavenProject = readAction { projectsManager.findContainingProject(projectDirectory) }
                         ?: return emptyList()
      val localRepository = blockingContext { projectsManager.repositoryPath }
      return blockingContext {
        mavenProject.declaredPluginInfos
          .mapNotNull { MavenArtifactUtil.readPluginInfo(it.artifact) }
          .flatMap { it.mojos }
          .map { TextCompletionInfo(it.displayName) }
          .sortedBy { it.text }
      }
    }

    override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
      return collectPhases() + collectGoals()
    }

    override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
      return collectCompletionInfo()
    }
  }

  private class ArgumentsCompletionTableInfo : CompletionTableInfo {
    override val emptyState: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.arguments.empty.text")

    override val dataColumnIcon: Icon? = null
    override val dataColumnName: String = MavenConfigurableBundle.message("maven.run.configuration.command.line.argument.column")

    override val descriptionColumnIcon: Icon? = null
    override val descriptionColumnName: String = MavenConfigurableBundle.message(
      "maven.run.configuration.command.line.description.column")

    private suspend fun collectOptionCompletion(isLongOptions: Boolean): List<TextCompletionInfo> {
      return blockingContext {
        MavenCommandLineOptions.getAllOptions()
          .map { TextCompletionInfo(it.getName(isLongOptions), it.description) }
          .sortedBy { it.text }
      }
    }

    override suspend fun collectCompletionInfo(): List<TextCompletionInfo> {
      return collectOptionCompletion(isLongOptions = false) +
             collectOptionCompletion(isLongOptions = true)
    }

    override suspend fun collectTableCompletionInfo(): List<TextCompletionInfo> {
      return collectOptionCompletion(isLongOptions = true)
    }
  }
}