// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

class UvUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  private val openProvider = UvOpenProvider()
  
  override val systemId: ProjectSystemId = UvConstants.SYSTEM_ID

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean {
    return Registry.`is`("python.project.model.uv") && openProvider.canOpenProject(buildFile)
  }

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val projectPath = Path.of(externalProjectPath)
    return project.service<UvSettings>().getLinkedProjects().any { it == projectPath }
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(UvSettingsListener.Companion.TOPIC, object : UvSettingsListener {
      override fun onLinkedProjectAdded(projectRoot: Path) = listener.onProjectLinked(projectRoot.toCanonicalPath())
      override fun onLinkedProjectRemoved(projectRoot: Path) = listener.onProjectUnlinked(projectRoot.toCanonicalPath())
    })
  }

  override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    openProvider.linkToExistingProjectAsync(externalProjectPath, project)
  }

  override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    openProvider.unlinkProject(project, externalProjectPath)
  }
}