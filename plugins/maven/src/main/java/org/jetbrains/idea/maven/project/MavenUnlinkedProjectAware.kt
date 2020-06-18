// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.PathUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

class MavenUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isBuildFile(buildFile: String): Boolean {
    val buildFileName = PathUtil.getFileName(buildFile)
    return MavenUtil.isPomFileName(buildFileName)
  }

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.projects.any { it.directory == externalProjectPath }
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    mavenProjectsManager.addProjectsTreeListener(ProjectsTreeListener(project, listener), parentDisposable)
  }

  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    MavenOpenProjectProvider().linkToExistingProject(externalProjectPath, project)
  }

  private class ProjectsTreeListener(project: Project, val listener: ExternalSystemProjectListener) : MavenProjectsTree.Listener {
    val mavenProjectsManager: MavenProjectsManager = MavenProjectsManager.getInstance(project)
    var mavenProjects = getMavenProjectPaths()

    override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
      updated.asSequence()
        .map { it.first }
        .filterNot { it.directory in mavenProjects }
        .forEach { listener.onProjectLinked(it.directory) }
      deleted.forEach { listener.onProjectUnlinked(it.directory) }
      mavenProjects = getMavenProjectPaths()
    }

    private fun getMavenProjectPaths() =
      mavenProjectsManager.projects.asSequence().map { it.directory }.toSet()
  }
}