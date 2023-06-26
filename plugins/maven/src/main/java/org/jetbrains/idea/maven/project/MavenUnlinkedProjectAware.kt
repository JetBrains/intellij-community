// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

internal class MavenUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean {
    return MavenUtil.isPomFile(project, buildFile)
  }

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

  @Deprecated("use async method instead")
  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    cs.launch {
      withContext(Dispatchers.Default) {
        linkAndLoadProjectAsync(project, externalProjectPath)
      }
    }
  }

  override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    MavenOpenProjectProvider().linkToExistingProjectAsync(externalProjectPath, project)
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