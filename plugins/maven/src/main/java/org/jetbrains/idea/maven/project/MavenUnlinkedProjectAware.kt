// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

internal class MavenUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun buildFileExtensions(): Array<String> = arrayOf(XmlFileType.DEFAULT_EXTENSION)

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean = MavenUtil.isPomFile(project, buildFile)

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.projects.any {
      VfsUtilCore.pathEqualsTo(it.directoryFile, externalProjectPath)
    }
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    mavenProjectsManager.addProjectsTreeListener(ProjectsTreeListener(project, listener), parentDisposable)
  }

  override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    MavenOpenProjectProvider().linkToExistingProjectAsync(externalProjectPath, project)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    MavenOpenProjectProvider().unlinkProject(project, externalProjectPath)
  }

  private class ProjectsTreeListener(project: Project, val listener: ExternalSystemProjectLinkListener) : MavenProjectsTree.Listener {
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
      mavenProjectsManager.projects.asSequence()
        .map { it.directory }
        .toCollection(CollectionFactory.createFilePathSet())
  }
}
