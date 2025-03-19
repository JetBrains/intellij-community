// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalProject
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.MavenPomFileChooserDescriptor
import org.jetbrains.idea.maven.execution.RunnerBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenWorkingDirectoryInfo(private val project: Project) : WorkingDirectoryInfo {
  override val editorLabel: String = ExecutionBundle.message("run.configuration.working.directory.label")

  override val settingsName: String = ExecutionBundle.message("run.configuration.working.directory.name")

  override val fileChooserDescriptor: FileChooserDescriptor
    get() = MavenPomFileChooserDescriptor(project).withTitle(RunnerBundle.message("maven.select.working.directory"))

  override val emptyFieldError: String = ExecutionBundle.message("run.configuration.working.directory.empty.error")

  override suspend fun collectExternalProjects(): List<ExternalProject> {
    val externalProjects = ArrayList<ExternalProject>()
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProjects = blockingContext {
      projectsManager.projects
    }
    for (mavenProject in mavenProjects) {
      val module = readAction {
        projectsManager.findModule(mavenProject)
      }
      if (module != null) {
        val path = blockingContext {
          mavenProject.directoryFile.path
        }
        externalProjects.add(ExternalProject(module.name, path))
      }
    }
    return externalProjects
  }
}
