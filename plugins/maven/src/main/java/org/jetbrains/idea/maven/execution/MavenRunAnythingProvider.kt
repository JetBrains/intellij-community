// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.*
import com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.getPath
import com.intellij.openapi.actionSystem.DataContext
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import javax.swing.Icon

class MavenRunAnythingProvider : RunAnythingCommandLineProvider() {

  override fun getHelpCommand() = HELP_COMMAND

  override fun getHelpIcon(): Icon = OpenapiIcons.RepositoryLibraryLogo


  override fun getHelpGroupTitle():  String {
    return "Maven" //NON-NLS
  }

  override fun getIcon(value: String): Icon = OpenapiIcons.RepositoryLibraryLogo

  override fun getCompletionGroupTitle() = RunnerBundle.message("popup.title.maven.goals")

  override fun getHelpCommandPlaceholder() = "mvn <goals...> <options...>"

  override fun getMainListItem(dataContext: DataContext, value: String) =
    RunAnythingMavenItem(getCommand(value), getIcon(value))

  override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
    val basicPhasesVariants = completeBasicPhases(commandLine).sorted()
    val customGoalsVariants = completeCustomGoals(dataContext, commandLine).sorted()
    val longOptionsVariants = completeOptions(commandLine, isLongOpt = true).sorted()
    val shortOptionsVariants = completeOptions(commandLine, isLongOpt = false).sorted()
    return when {
      commandLine.toComplete.startsWith("--") ->
        longOptionsVariants + shortOptionsVariants + basicPhasesVariants + customGoalsVariants
      commandLine.toComplete.startsWith("-") ->
        shortOptionsVariants + longOptionsVariants + basicPhasesVariants + customGoalsVariants
      else ->
        basicPhasesVariants + customGoalsVariants + longOptionsVariants + shortOptionsVariants
    }
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val project = fetchProject(dataContext)
    val context = dataContext.getData(EXECUTING_CONTEXT) ?: ProjectContext(project)
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = context.getMavenProject(projectsManager)
    val workingDirPath = mavenProject?.directory ?: context.getPath() ?: return false
    val pomFileName = mavenProject?.file?.name ?: MavenConstants.POM_XML
    val explicitProfiles = projectsManager.explicitProfiles
    val enabledProfiles = explicitProfiles.enabledProfiles
    val disabledProfiles = explicitProfiles.disabledProfiles
    val goals = commandLine.parameters
    val params = MavenRunnerParameters(true, workingDirPath, pomFileName, goals, enabledProfiles, disabledProfiles)
    val mavenRunner = MavenRunner.getInstance(project)
    mavenRunner.run(params, mavenRunner.settings, null)
    return true
  }

  private fun RunAnythingContext.getMavenProject(projectsManager: MavenProjectsManager) = when (this) {
    is ProjectContext -> projectsManager.rootProjects.firstOrNull()
    is ModuleContext -> projectsManager.findProject(module)
    is RecentDirectoryContext -> null
    is BrowseRecentDirectoryContext -> null
  }

  private fun completeOptions(commandLine: CommandLine, isLongOpt: Boolean): Sequence<String> {
    return MavenCommandLineOptions.getAllOptions().asSequence()
      .mapNotNull { it.getName(isLongOpt) }
      .filter { it !in commandLine }
  }

  private fun completeBasicPhases(commandLine: CommandLine): Sequence<String> {
    return MavenConstants.BASIC_PHASES.asSequence().filter { it !in commandLine }
  }

  private fun completeCustomGoals(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
    val project = fetchProject(dataContext)
    val context = dataContext.getData(RunAnythingProvider.EXECUTING_CONTEXT) ?: ProjectContext(project)
    val projectsManager = MavenProjectsManager.getInstance(project)
    if (!projectsManager.isMavenizedProject) return emptySequence()
    val mavenProject = context.getMavenProject(projectsManager) ?: return emptySequence()
    val localRepository = projectsManager.repositoryPath
    return mavenProject.declaredPluginInfos.asSequence()
      .mapNotNull { MavenArtifactUtil.readPluginInfo(it.artifact) }
      .flatMap { it.mojos.asSequence() }
      .map { it.displayName }
      .filter { it !in commandLine }
  }

  companion object {
    const val HELP_COMMAND = "mvn"
  }
}
