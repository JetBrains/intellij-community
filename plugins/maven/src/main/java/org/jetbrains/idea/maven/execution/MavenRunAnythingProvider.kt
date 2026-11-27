// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.BrowseRecentDirectoryContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ModuleContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ProjectContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.RecentDirectoryContext
import com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.getPath
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.util.execution.ParametersListUtil
import icons.OpenapiIcons
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.annotations.ApiStatus
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

  override fun findMatchingValue(dataContext: DataContext, pattern: String): String? {
    super.findMatchingValue(dataContext, pattern)?.let { return it }
    val (_, command) = splitEnvironment(pattern) ?: return null
    return super.findMatchingValue(dataContext, command)?.let { getCommand(pattern) }
  }

  override fun run(dataContext: DataContext, value: String): Boolean {
    val (environment, command) = splitEnvironment(value) ?: return super.run(dataContext, value)
    return super.run(SimpleDataContext.getSimpleContext(ENVIRONMENT, environment, dataContext), command)
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val environment = dataContext.getData(ENVIRONMENT).orEmpty()
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
    val settings = if (environment.isEmpty()) mavenRunner.settings
    else mavenRunner.settings.clone().apply { environmentProperties = environmentProperties + environment }
    mavenRunner.run(params, settings, null)
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

  @ApiStatus.Internal
  data class CommandWithEnvironment(val environment: Map<String, String>, val command: String)

  companion object {
    const val HELP_COMMAND = "mvn"

    private val ENVIRONMENT = DataKey.create<Map<String, String>>("MavenRunAnythingProvider.environment")
    private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private const val SHELL_EXPANSION_CHARS = $$"$`~"

    @ApiStatus.Internal
    fun splitEnvironment(line: String): CommandWithEnvironment? {
      val trimmedLine = line.trimStart()
      val firstEquals = trimmedLine.indexOf('=')
      if (firstEquals < 0 || !ENVIRONMENT_NAME.matches(trimmedLine.subSequence(0, firstEquals))) return null

      val tokens = ParametersListUtil.parse(line, true, true)
      val environment = LinkedHashMap<String, String>()
      var commandStart = 0
      for (token in tokens) {
        val name = token.substringBefore('=', missingDelimiterValue = "")
        if (!ENVIRONMENT_NAME.matches(name)) break
        if (token.any { it in SHELL_EXPANSION_CHARS }) return null
        commandStart = line.indexOf(token, commandStart) + token.length
        environment[name] = ParametersListUtil.parse(token, false, true).singleOrNull()?.substringAfter('=') ?: return null
      }
      return CommandWithEnvironment(environment, line.substring(commandStart).trim())
    }
  }
}
