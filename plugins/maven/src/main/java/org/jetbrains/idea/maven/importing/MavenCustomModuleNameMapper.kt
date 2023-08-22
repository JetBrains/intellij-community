// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.getInstance
import org.jetbrains.idea.maven.importing.workspaceModel.MavenCustomModuleNameMappingEntity
import org.jetbrains.idea.maven.importing.workspaceModel.MavenCustomModuleNameMappingEntitySource
import org.jetbrains.idea.maven.importing.workspaceModel.modifyEntity
import org.jetbrains.idea.maven.project.MavenProjectsManager

@Service(Service.Level.PROJECT)
internal class MavenCustomModuleNameMapper(private val project: Project) {
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  @RequiresWriteLock
  fun onModuleRename(module: Module) {
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(module)
    if (null == mavenProject) return
    val entity = getOrCreateEntity()
    val defaultModuleName = MavenModuleNameMapper.getDefaultModuleName(mavenProject)
    val moduleName = module.name
    val fileUrl = mavenProject.file.toVirtualFileUrl(virtualFileUrlManager)
    if (defaultModuleName == moduleName) {
      removeMapping(entity, fileUrl)
    }
    else {
      addMapping(entity, fileUrl, moduleName)
    }
  }

  @RequiresWriteLock
  private fun removeMapping(entity: MavenCustomModuleNameMappingEntity, fileUrl: VirtualFileUrl) {
    val moduleNames = entity.customModuleNames.toMutableMap()
    moduleNames.remove(fileUrl)
    WorkspaceModel.getInstance(project).updateProjectModel("Remove mapping from MavenCustomModuleNameMappingEntity") { builder ->
      builder.modifyEntity(entity) { customModuleNames = moduleNames }
    }
  }

  @RequiresWriteLock
  private fun addMapping(entity: MavenCustomModuleNameMappingEntity, fileUrl: VirtualFileUrl, moduleName: @NlsSafe String) {
    val moduleNames = entity.customModuleNames.toMutableMap()
    moduleNames[fileUrl] = moduleName
    WorkspaceModel.getInstance(project).updateProjectModel("Add mapping to MavenCustomModuleNameMappingEntity") { builder ->
      builder.modifyEntity(entity) { customModuleNames = moduleNames }
    }
  }

  @RequiresWriteLock
  private fun getOrCreateEntity(): MavenCustomModuleNameMappingEntity {
    if (!getEntities().iterator().hasNext()) {
      createEntity()
    }
    return getEntities().iterator().next()
  }

  @RequiresWriteLock
  private fun createEntity() {
    WorkspaceModel.getInstance(project).updateProjectModel("Add MavenCustomModuleNameMappingEntity") { builder ->
      builder.addEntity(MavenCustomModuleNameMappingEntity(emptyMap(), MavenCustomModuleNameMappingEntitySource))
    }
  }

  private fun getEntities() = WorkspaceModel.getInstance(project).currentSnapshot.entities(MavenCustomModuleNameMappingEntity::class.java)

  fun getCustomModuleNames(): Map<VirtualFile, String> {
    val iterator = getEntities().iterator()
    if (!iterator.hasNext()) {
      return emptyMap()
    }
    return iterator.next().customModuleNames.mapKeys { it.key.virtualFile }.filterKeys { it != null }.mapKeys { it.key!! }
  }
}