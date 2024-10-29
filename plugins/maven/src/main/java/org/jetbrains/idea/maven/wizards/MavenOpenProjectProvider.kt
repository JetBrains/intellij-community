// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.observation.trackActivity
import icons.OpenapiIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenActivityKey
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path
import javax.swing.Icon

class MavenOpenProjectProvider : AbstractOpenProjectProvider() {
  override val systemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  fun getName(): String {
    return MavenProjectBundle.message("maven.name")
  }

  fun getIcon(): Icon {
    return OpenapiIcons.RepositoryLibraryLogo
  }

  override fun isProjectFile(file: VirtualFile): Boolean {
    return MavenUtil.isPomFile(file)
  }

  override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
    val autoImportDisabled = Registry.`is`("external.system.auto.import.disabled")
    if (autoImportDisabled) {
      LOG.debug("External system auto import disabled. Adding '$projectFile' to existing project ${project.name}, but not syncing")
    }
    val syncProject = !autoImportDisabled

    doLinkToExistingProjectAsync(projectFile, project, syncProject)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val folder = VirtualFileManager.getInstance().findFileByNioPath(Path.of(externalProjectPath)) ?: return
    val projectPomFile = projectsManager.projects.firstOrNull { it.file.parent == folder }?.file ?: return
    withContext(Dispatchers.EDT) {
      projectsManager.removeManagedFiles(listOf(projectPomFile), null, null)
    }
  }

  @ApiStatus.Internal
  suspend fun forceLinkToExistingProjectAsync(projectFilePath: String, project: Project) {
    forceLinkToExistingProjectAsync(getProjectFile(projectFilePath), project)
  }

  @ApiStatus.Internal
  suspend fun forceLinkToExistingProjectAsync(projectFile: VirtualFile, project: Project) {
    val projectRoot = if (projectFile.isDirectory) projectFile else projectFile.parent
    unlinkOtherLinkedProjects(project, projectRoot)
    doLinkToExistingProjectAsync(projectFile, project, true)
  }

  private suspend fun doLinkToExistingProjectAsync(projectFile: VirtualFile, project: Project, syncProject: Boolean) {
    LOG.debug("Link Maven project '$projectFile' to existing project ${project.name}")

    val projectRoot = if (projectFile.isDirectory) projectFile else projectFile.parent

    if (ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProjectAsync(project, systemId, projectRoot.toNioPath())) {
      val asyncBuilder = MavenProjectAsyncBuilder()
      project.trackActivity(MavenActivityKey) {
        asyncBuilder.commit(project, projectFile, null, syncProject)
      }
    }
  }

}