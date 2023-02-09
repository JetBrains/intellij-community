// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.vfs.VirtualFilePrefixTreeFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

class MavenTrustedProjectsLocator : TrustedProjectsLocator {

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