// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.projectModel.poetry.PoetryProjectAware.CoroutineScopeService.Companion.coroutineScope
import com.jetbrains.python.sdk.poetry.PY_PROJECT_TOML
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Tracks changes in pyproject.toml files and suggests syncing their changes with the project model
 * according to the `Settings | Build, Execution, Deployment | Build Tools` settings.
 */
class PoetryProjectAware(
  private val project: Project,
  override val projectId: ExternalSystemProjectId,
) : ExternalSystemProjectAware {

  override val settingsFiles: Set<String>
    get() = collectSettingFiles()

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(PoetrySyncListener.TOPIC, object : PoetrySyncListener {
      override fun onStart(projectRoot: Path) = listener.onProjectReloadStart()
      override fun onFinish(projectRoot: Path) = listener.onProjectReloadFinish(status = ExternalSystemRefreshStatus.SUCCESS)
    })
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    project.coroutineScope.launchTracked { 
      PoetryProjectModelService.syncProjectModelRoot(project, Path.of(projectId.externalProjectPath))
    }
  }

  // Called after sync
  private fun collectSettingFiles(): Set<String> {
    val source = PoetryEntitySource(projectId.externalProjectPath.toVirtualFileUrl(project))
    return project.workspaceModel.currentSnapshot
      .entities<ContentRootEntity>()
      .filter { it.entitySource == source }
      .map { it.url.toPath() }
      .map { it.resolve(PY_PROJECT_TOML) }
      .map { it.toCanonicalPath() }
      .toSet()
  }

  private fun String.toVirtualFileUrl(project: Project): VirtualFileUrl {
    return Path.of(this).toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.coroutineScope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
  
  private class PoetrySyncStartupActivity: ProjectActivity {
    init {
      if (!Registry.`is`("python.project.model.poetry")) {
        throw ExtensionNotApplicableException.create()
      }
    }
    
    override suspend fun execute(project: Project) {
      val projectTracker = ExternalSystemProjectTracker.getInstance(project)
      project.service<PoetrySettings>().getLinkedProjects().forEach { projectRoot ->
        val projectId = ExternalSystemProjectId(PoetryConstants.SYSTEM_ID, projectRoot.toCanonicalPath())
        val projectAware = PoetryProjectAware(project, projectId)
        projectTracker.register(projectAware)
        projectTracker.activate(projectId)
      }
    }
  }
  
  private class PoetryListener(private val project: Project): PoetrySettingsListener {
    init {
      if (!Registry.`is`("python.project.model.poetry")) {
        throw ExtensionNotApplicableException.create()
      }
    }
    
    override fun onLinkedProjectAdded(projectRoot: Path) {
      val projectTracker = ExternalSystemProjectTracker.getInstance(project)
      val projectId = ExternalSystemProjectId(PoetryConstants.SYSTEM_ID, projectRoot.toCanonicalPath())
      val projectAware = PoetryProjectAware(project, projectId)
      projectTracker.register(projectAware)
      projectTracker.activate(projectId)
    }

    override fun onLinkedProjectRemoved(projectRoot: Path) {
      val projectId = ExternalSystemProjectId(PoetryConstants.SYSTEM_ID, projectRoot.toCanonicalPath())
      ExternalSystemProjectTracker.getInstance(project).remove(projectId)
    }
  }
}