// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.jetbrains.python.projectModel.BaseProjectModelService
import com.jetbrains.python.projectModel.ProjectModelSettings
import com.jetbrains.python.projectModel.ProjectModelSyncListener
import com.jetbrains.python.projectModel.PythonProjectModelResolver
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Syncs the project model described in pyproject.toml files with the IntelliJ project model.
 */
object UvProjectModelService : BaseProjectModelService<UvEntitySource, UvProject>() {
  override val projectModelResolver: PythonProjectModelResolver<UvProject>
    get() = UvProjectModelResolver

  override val systemName: @NlsSafe String
    get() = "Uv"

  override fun getSettings(project: Project): ProjectModelSettings = project.service<UvSettings>()

  override fun getSyncListener(project: Project): ProjectModelSyncListener = project.messageBus.syncPublisher(UvSyncListener.TOPIC)

  override fun createEntitySource(project: Project, singleProjectRoot: Path): EntitySource {
    val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    return UvEntitySource(singleProjectRoot.toVirtualFileUrl(fileUrlManager))
  }

  override fun getEntitySourceClass(): KClass<out EntitySource> = UvEntitySource::class

  fun findWorkspace(module: Module): UvWorkspace<Module>? {
    val wsmSnapshot = module.project.workspaceModel.currentSnapshot
    val moduleEntity = wsmSnapshot.resolve(ModuleId(module.name))!!
    val workspace = findWorkspace(module.project, moduleEntity)
    if (workspace == null) {
      return null
    }
    return UvWorkspace(
      root = workspace.root.findModule(wsmSnapshot)!!,
      members = workspace.members.mapNotNull { it.findModule(wsmSnapshot) }.toSet(),
    )
  }

  fun findWorkspace(project: Project, module: ModuleEntity): UvWorkspace<ModuleEntity>? {
    val fullName = module.exModuleOptions?.linkedProjectId
    if (fullName == null) return null
    val workspaceName = fullName.split(":")[0]
    val currentSnapshot = project.workspaceModel.currentSnapshot
    val rootModule = currentSnapshot.resolve(ModuleId(workspaceName))
    return UvWorkspace(
      root = rootModule!!,
      members = currentSnapshot.entitiesBySource { it is UvEntitySource }
        .filterIsInstance<ModuleEntity>()
        .filter {
          val externalId = it.exModuleOptions?.linkedProjectId
          externalId != null && externalId.startsWith("$workspaceName:")
        }
        .toSet(),
    )
  }

  data class UvWorkspace<T>(val root: T, val members: Set<T>)
}
