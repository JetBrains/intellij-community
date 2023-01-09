// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.impl.trustedProjects.ProjectLocator
import com.intellij.openapi.file.VirtualFileUtil.toNioPathOrNull
import com.intellij.openapi.file.converter.VirtualFilePrefixTreeFactory
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

class MavenProjectLocator : ProjectLocator {

  override fun getProjectRoots(project: Project): List<Path> {
    val projectRoots = VirtualFilePrefixTreeFactory.createSet()
    val projectsManager = MavenProjectsManager.getInstance(project)
    for (mavenProject in projectsManager.projects) {
      projectRoots.add(mavenProject.directoryFile)
    }
    return projectRoots.getRoots().mapNotNull { it.toNioPathOrNull() }
  }

  override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> {
    if (project == null) {
      return emptyList()
    }
    return getProjectRoots(project)
  }
}