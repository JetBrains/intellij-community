// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin.utils

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.performancePlugin.dto.MavenGoalConfigurationDto
import org.jetbrains.idea.maven.project.MavenProjectsManager

object MavenConfigurationUtils {

  fun createRunnerParams(project: Project, settings: MavenGoalConfigurationDto): MavenRunnerParameters {
    val projectsManager = MavenProjectsManager.getInstance(project)

    val mavenProject = projectsManager
      .projects
      .firstOrNull { it.displayName == settings.moduleName }

    if (mavenProject == null) {
      throw IllegalArgumentException(
        "There is no module with name ${settings.moduleName}.")
    }

    return MavenRunnerParameters(true,
                                 mavenProject.directory,
                                 mavenProject.file.getName(),
                                 settings.goals,
                                 projectsManager.explicitProfiles)
  }
}