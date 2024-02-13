// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.run.configuration.MavenRunConfigurationSettingsEditor
import org.jetbrains.idea.maven.project.MavenProjectsManager

class EditMavenGoalCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "editMavenGoal"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override fun getName(): String {
    return NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {

    //val list = RunManager.getInstance(project).getConfigurationsList(MavenRunConfigurationType.getInstance())
    val moduleName = "test-resources"
    val goals = listOf("test")
    val project = context.project
    val projectsManager = MavenProjectsManager.getInstance(project)
    //if (projectsManager == null) {
    //  promise.setError("There is no MavenProjectsManager for project")
    //  return@invokeLater
    //}

    val currentProjects = projectsManager.projects
    val mavenProject = currentProjects.firstOrNull { it.displayName == moduleName }!!
    //if (mavenProject == null) {
    //  promise.setError(
    //    "There is no module with name $moduleName. Actual modules: ${currentProjects.joinToString("\n") { it.displayName }}")
    //  return@invokeLater
    //}

    val explicitProfiles = projectsManager.getExplicitProfiles()

    val params = MavenRunnerParameters(true,
                                       mavenProject.directory,
                                       mavenProject.file.getName(),
                                       goals,
                                       explicitProfiles.enabledProfiles,
                                       explicitProfiles.disabledProfiles)
    val configSettings = MavenRunConfigurationType.createRunnerAndConfigurationSettings(null,
                                                                                        null,
                                                                                        params,
                                                                                        project,
                                                                                        MavenRunConfigurationType.generateName(project, params),
                                                                                        false)
    //configSettings.configuration.configurationEditor
    withContext(Dispatchers.EDT) {
      val configurable = SingleConfigurationConfigurable.editSettings<RunConfiguration>(configSettings, null)
      //(configurable.editor as MavenRunConfigurationSettingsEditor)
      (configurable.configuration as MavenRunConfiguration).generalSettings?.setUserSettingsFile("jjnjnj")
      //if (configurable.isModified) configurable.apply()
    }

  }
}