// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.*
import com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.ide.actions.runAnything.getPath
import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.util.execution.ParametersListUtil
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import java.util.Collections.emptyList
import javax.swing.Icon

class MavenRunAnythingProvider : RunAnythingProviderBase<String>() {

  override fun getCommand(value: String) = value

  override fun getHelpCommand() = HELP_COMMAND

  override fun getHelpIcon(): Icon? = OpenapiIcons.RepositoryLibraryLogo

  override fun getHelpGroupTitle() = "Maven"

  override fun getIcon(value: String): Icon? = OpenapiIcons.RepositoryLibraryLogo

  override fun getCompletionGroupTitle() = "Maven goals"

  override fun getHelpItem(dataContext: DataContext) =
    RunAnythingHelpItem(helpCommandPlaceholder, helpCommand, helpDescription, helpIcon)

  override fun getHelpCommandPlaceholder() = "mvn <goals...> <options...>"

  override fun getMainListItem(dataContext: DataContext, value: String) =
    RunAnythingMavenItem(getCommand(value), getIcon(value))

  override fun findMatchingValue(dataContext: DataContext, pattern: String) =
    if (pattern.startsWith(helpCommand)) getCommand(pattern) else null

  override fun getValues(dataContext: DataContext, pattern: String): List<String> {
    val commandLine = parseCommandLine(pattern) ?: return emptyList()
    val basicPhasesVariants = completeBasicPhases(commandLine)
    val customGoalsVariants = completeCustomGoals(dataContext, commandLine)
    val longOptionsVariants = completeOptions(commandLine, isLongOpt = true)
    val shortOptionsVariants = completeOptions(commandLine, isLongOpt = false)
    val completion = when {
      commandLine.toComplete.startsWith("--") ->
        longOptionsVariants + shortOptionsVariants + basicPhasesVariants + customGoalsVariants
      commandLine.toComplete.startsWith("-") ->
        shortOptionsVariants + longOptionsVariants + basicPhasesVariants + customGoalsVariants
      else ->
        basicPhasesVariants + customGoalsVariants + longOptionsVariants + shortOptionsVariants
    }
    val prefix = commandLine.prefix
    return completion.map { if (prefix.isEmpty()) "$helpCommand $it" else "$helpCommand $prefix $it" }
  }

  override fun execute(dataContext: DataContext, value: String) {
    val project = fetchProject(dataContext)
    val context = dataContext.getData(RunAnythingProvider.EXECUTING_CONTEXT) ?: ProjectContext(project)
    val commandLine = parseCommandLine(value)!!
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = context.getMavenProject(projectsManager)
    val workingDirPath = mavenProject?.directory ?: context.getPath()
    if (workingDirPath == null) {
      Messages.showWarningDialog(
        project,
        IdeBundle.message("run.anything.notification.warning.content", commandLine.command),
        IdeBundle.message("run.anything.notification.warning.title"))
      return
    }
    val pomFileName = mavenProject?.file?.name ?: MavenConstants.POM_XML
    val explicitProfiles = projectsManager.explicitProfiles
    val params = MavenRunnerParameters(
      /*isPomExecution = */ true,
      /*workingDirPath = */ workingDirPath,
      /*pomFileName = */ pomFileName,
      /*goals = */ commandLine.commands,
      /*explicitEnabledProfiles = */ explicitProfiles.enabledProfiles,
      /*explicitDisabledProfiles = */ explicitProfiles.disabledProfiles
    )
    val mavenRunner = MavenRunner.getInstance(project)
    mavenRunner.run(params, mavenRunner.settings, null)
  }

  private fun RunAnythingContext.getMavenProject(projectsManager: MavenProjectsManager) = when (this) {
    is ProjectContext -> projectsManager.rootProjects.firstOrNull()
    is ModuleContext -> projectsManager.findProject(module)
    is RecentDirectoryContext -> null
    is BrowseRecentDirectoryContext -> null
  }

  // list available cl options
  private fun completeOptions(commandLine: CommandLine, isLongOpt: Boolean): List<String> {
    return MavenCommandLineOptions.getAllOptions()
      .mapNotNull { it.getName(isLongOpt) }
      .filter { it !in commandLine }
  }

  // list basic phases
  private fun completeBasicPhases(commandLine: CommandLine): List<String> {
    return MavenConstants.BASIC_PHASES.filter { it !in commandLine }
  }

  // list plugin-specific goals
  private fun completeCustomGoals(dataContext: DataContext, commandLine: CommandLine): List<String> {
    val project = fetchProject(dataContext)
    val context = dataContext.getData(RunAnythingProvider.EXECUTING_CONTEXT) ?: ProjectContext(project)
    val projectsManager = MavenProjectsManager.getInstance(project)
    if (!projectsManager.isMavenizedProject) return emptyList()
    val mavenProject = context.getMavenProject(projectsManager) ?: return emptyList()
    val localRepository = projectsManager.localRepository
    return mavenProject.declaredPlugins
      .mapNotNull { MavenArtifactUtil.readPluginInfo(localRepository, it.mavenId) }
      .flatMap { it.mojos }
      .map { it.displayName }
      .filter { it !in commandLine }
  }

  private fun parseCommandLine(commandLine: String): CommandLine? {
    val command = when {
      commandLine.startsWith(helpCommand) -> trimStart(commandLine, helpCommand)
      helpCommand.startsWith(commandLine) -> ""
      else -> return null
    }
    val prefix = notNullize(substringBeforeLast(command, " "), "").trim()
    val toComplete = notNullize(substringAfterLast(command, " "), "").trim()
    val commands = ParametersListUtil.parse(prefix)
    return CommandLine(commands, command, prefix, toComplete)
  }

  private data class CommandLine(
    val commands: List<String>,
    val command: String,
    val prefix: String,
    val toComplete: String
  ) {
    private val commandSet = commands.toSet()

    operator fun contains(command: String) = command in commandSet
  }

  companion object {
    const val HELP_COMMAND = "mvn"
  }
}
