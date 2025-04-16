// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Syncs the project model described in pyproject.toml files with the IntelliJ project model.
 */
abstract class BaseProjectModelService<E : EntitySource> {
  abstract val systemName: @NlsSafe String

  abstract val projectModelResolver: PythonProjectModelResolver

  abstract fun getSettings(project: Project): ProjectModelSettings

  protected abstract fun getSyncListener(project: Project): ProjectModelSyncListener

  protected abstract fun createEntitySource(project: Project, singleProjectRoot: Path): EntitySource

  protected abstract fun getEntitySourceClass(): KClass<out EntitySource>
  
  suspend fun linkAllProjectModelRoots(project: Project, basePath: @SystemIndependent @NonNls String) {
    val allProjectRoots = withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.discovering.projects", systemName)) {
      projectModelResolver.discoverIndependentProjectGraphs(Path.of(basePath)).map { it.root }
    }
    getSettings(project).setLinkedProjects(allProjectRoots)
  }

  suspend fun syncAllProjectModelRoots(project: Project) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.all.projects", systemName)) {
      // TODO progress bar, listener with events
      getSettings(project).getLinkedProjects().forEach {
        syncProjectModelRootImpl(project, it)
      }
    }
  }

  suspend fun syncProjectModelRoot(project: Project, projectModelRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.syncing.projects.at", systemName, projectModelRoot)) {
      syncProjectModelRootImpl(project, projectModelRoot)
    }
  }

  suspend fun forgetProjectModelRoot(project: Project, projectModelRoot: Path) {
    withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.unlinking.projects.at", systemName, projectModelRoot)) {
      getSettings(project).removeLinkedProject(projectModelRoot)
      forgetProjectModelRootImpl(project, projectModelRoot)
    }
  }
  
  private suspend fun forgetProjectModelRootImpl(project: Project, projectModelRoot: Path) {
    val source = createEntitySource(project, projectModelRoot)
    project.workspaceModel.update("Forgetting a $systemName project at $projectModelRoot") { storage ->
      storage.replaceBySource({ it == source }, MutableEntityStorage.Companion.create())
    }
  }

  private suspend fun syncProjectModelRootImpl(project: Project, projectRoot: Path) {
    val listener = getSyncListener(project)
    listener.onStart(projectRoot)
    try {
      val source = createEntitySource(project, projectRoot)
      val graph = projectModelResolver.discoverIndependentProjectGraphs(projectRoot)
      if (graph.isEmpty()) {
        return
      }
      val allModules = graph.flatMap { it.modules }
      val storage = createProjectModel(project, allModules, source)
      project.workspaceModel.update("$systemName sync at ${projectRoot}") { mutableStorage ->
        // Fake module entity is added by default if nothing was discovered
        if (allModules.any { it.root == project.baseNioPath }) {
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
    source: EntitySource,
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
   * (that's going to be replaced with a module belonging to a specific project management system).
   */
  fun removeFakeModuleEntity(project: Project, storage: MutableEntityStorage) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val basePathUrl = project.baseNioPath?.toVirtualFileUrl(virtualFileUrlManager) ?: return
    val contentRoots = storage
      .entitiesBySource { getEntitySourceClass().isInstance(it)  }
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