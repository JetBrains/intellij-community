// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.jetbrains.python.PyBundle
import com.jetbrains.python.projectModel.ModuleDescriptor
import java.nio.file.Path

/**
 * Syncs the project model described in pyproject.toml files with the IntelliJ project model.
 */
object UvProjectResolver {
  suspend fun syncAllUvProjects(project: Project) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.all.uv.projects")) {
      // TODO progress bar, listener with events
      project.service<UvSettings>().getLinkedProjects().forEach {
        syncUvProjectImpl(project, it)
      }
    }
  }

  suspend fun syncUvProject(project: Project, projectRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.uv.projects.at", projectRoot)) {
      syncUvProjectImpl(project, projectRoot)
    }
  }

  suspend fun forgetUvProject(project: Project, projectRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.unlinking.uv.projects.at", projectRoot)) {
      project.service<UvSettings>().removeLinkedProject(projectRoot)
      forgetUvProjectImpl(project, projectRoot)
    }
  }

  private suspend fun forgetUvProjectImpl(project: Project, projectRoot: Path) {
    val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val source = UvEntitySource(projectRoot.toVirtualFileUrl(fileUrlManager))
    project.workspaceModel.update("Forgetting a uv project at $projectRoot") { storage ->
      storage.replaceBySource({ it == source }, MutableEntityStorage.Companion.create())
    }
  }

  /**
   * Synchronizes the uv project by creating and updating module entities in the workspace model of the given project.
   *
   * @param project The IntelliJ IDEA project that needs synchronization.
   * @param projectRoot The root path of the uv project tree to be synchronized.
   */
  private suspend fun syncUvProjectImpl(project: Project, projectRoot: Path) {
    val listener = project.messageBus.syncPublisher(UvSyncListener.Companion.TOPIC)
    listener.onStart(projectRoot)
    try {
      val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
      val source = UvEntitySource(projectRoot.toVirtualFileUrl(fileUrlManager))
      val graph = UvProjectRootResolver.discoverProjectRoot(projectRoot)
      val storage = createProjectModel(project, graph?.modules.orEmpty(), source)

      project.workspaceModel.update("Uv sync at ${projectRoot}") { mutableStorage ->
        // Fake module entity is added by default if nothing was discovered
        if (projectRoot == project.baseNioPath) {
          removeFakeModuleEntity(project, mutableStorage)
        }
        mutableStorage.replaceBySource({ it == source }, storage)
      }
    }
    finally {
      listener.onFinish(projectRoot)
    }
  }

  private fun createProjectModel(
    project: Project,
    graph: List<ModuleDescriptor>,
    source: UvEntitySource,
  ): EntityStorage {
    val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val storage = MutableEntityStorage.create()
    for (module in graph) {
      val existingModuleEntity = project.workspaceModel.currentSnapshot
        .entitiesBySource { it == source }
        .filterIsInstance<ModuleEntity>()
        .find { it.name == module.name }
      val existingSdkEntity = existingModuleEntity
        ?.dependencies
        ?.find { it is SdkDependency } as? SdkDependency
      val sdkDependency = existingSdkEntity ?: InheritedSdkDependency
      storage addEntity ModuleEntity(module.name, emptyList(), source) {
        dependencies += sdkDependency
        dependencies += ModuleSourceDependency
        for (moduleName in module.moduleDependencies) {
          dependencies += ModuleDependency(ModuleId(moduleName.name), true, DependencyScope.COMPILE, false)
        }
        contentRoots = listOf(ContentRootEntity(module.root.toVirtualFileUrl(fileUrlManager), emptyList(), source))
      }
    }
    return storage
  }

  /**
   * Removes the default IJ module created for the root of the project 
   * (that's going to be replaced with another module managed by uv).
   */
  fun removeFakeModuleEntity(project: Project, storage: MutableEntityStorage) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val basePathUrl = project.baseNioPath?.toVirtualFileUrl(virtualFileUrlManager) ?: return
    val contentRoots = storage
      .entitiesBySource { it !is UvEntitySource }
      .filterIsInstance<ContentRootEntity>()
      .filter { it.url == basePathUrl }
      .toList()
    for (entity in contentRoots) {
      storage.removeEntity(entity.module)
      storage.removeEntity(entity)
    }
  }
  
  private val Project.baseNioPath: Path?
    get() = basePath?.let { Path.of(it) }
}