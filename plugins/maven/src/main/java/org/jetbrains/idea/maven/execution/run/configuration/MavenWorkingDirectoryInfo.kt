// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalProject
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.execution.MavenPomFileChooserDescriptor
import org.jetbrains.idea.maven.execution.RunnerBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenWorkingDirectoryInfo(project: Project) : WorkingDirectoryInfo {
  override val editorLabel: String = ExecutionBundle.message("run.configuration.working.directory.label")

  override val settingsName: String = ExecutionBundle.message("run.configuration.working.directory.name")

  override val fileChooserTitle: String = RunnerBundle.message("maven.select.working.directory")
  override val fileChooserDescriptor: FileChooserDescriptor = MavenPomFileChooserDescriptor(project)

  override val emptyFieldError: String = ExecutionBundle.message("run.configuration.working.directory.empty.error")

  override val externalProjects: List<ExternalProject> by lazy {
    ArrayList<ExternalProject>().apply {
      val projectsManager = MavenProjectsManager.getInstance(project)
      for (mavenProject in projectsManager.projects) {
        val module = projectsManager.findModule(mavenProject)
        if (module != null) {
          val path = FileUtil.toCanonicalPath(mavenProject.directory)
          add(ExternalProject(module.name, path))
        }
      }
    }
  }
}