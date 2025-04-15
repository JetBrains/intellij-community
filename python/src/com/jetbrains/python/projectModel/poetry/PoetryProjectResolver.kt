// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.jetbrains.python.PyBundle
import java.nio.file.Path
import kotlin.collections.plusAssign

/**
 * Syncs the project model described in pyproject.toml files with the IntelliJ project model.
 */
object PoetryProjectResolver {
  suspend fun syncAllPoetryProjects(project: Project) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.all.poetry.projects")) {
      // TODO progress bar, listener with events
      project.service<PoetrySettings>().getLinkedProjects().forEach {
        syncPoetryProjectImpl(project, it)
      }
    }
  }

  suspend fun syncPoetryProject(project: Project, projectRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.poetry.projects.at", projectRoot)) {
      syncPoetryProjectImpl(project, projectRoot)
    }
  }

  suspend fun forgetPoetryProject(project: Project, projectRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.unlinking.poetry.projects.at", projectRoot)) {
      project.service<PoetrySettings>().removeLinkedProject(projectRoot)
      forgetPoetryProjectImpl(project, projectRoot)
    }
  }

  private suspend fun forgetPoetryProjectImpl(project: Project, projectRoot: Path) {
    val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val source = PoetryEntitySource(projectRoot.toVirtualFileUrl(fileUrlManager))
    project.workspaceModel.update("Forgetting a Poetry project at $projectRoot") { storage ->
      storage.replaceBySource({ it == source }, MutableEntityStorage.Companion.create())
    }
  }

  /**
   * Synchronizes the poetry project by creating and updating module entities in the workspace model of the given project.
   *
   * @param project The IntelliJ IDEA project that needs synchronization.
   * @param projectRoot The root path of the poetry project tree to be synchronized.
   */
  private suspend fun syncPoetryProjectImpl(project: Project, projectRoot: Path) {
    val listener = project.messageBus.syncPublisher(PoetrySyncListener.TOPIC)
    listener.onStart(projectRoot)
    try {
      val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
      val source = PoetryEntitySource(projectRoot.toVirtualFileUrl(fileUrlManager))
      val graph = readProjectModelRoot(projectRoot)
      val storage = createProjectModel(project, graph?.modules.orEmpty(), source)

      project.workspaceModel.update("Poetry sync at ${projectRoot}") { mutableStorage ->
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
    source: PoetryEntitySource,
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
          dependencies += ModuleDependency(ModuleId(moduleName), true, DependencyScope.COMPILE, false)
        }
        contentRoots = listOf(ContentRootEntity(module.root.toVirtualFileUrl(fileUrlManager), emptyList(), source))
      }
    }
    return storage
  }

  /**
   * Removes the default IJ module created for the root of the project 
   * (that's going to be replaced with another module managed by Poetry).
   */
  fun removeFakeModuleEntity(project: Project, storage: MutableEntityStorage) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val basePathUrl = project.baseNioPath?.toVirtualFileUrl(virtualFileUrlManager) ?: return
    val contentRoots = storage
      .entitiesBySource { it !is PoetryEntitySource }
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