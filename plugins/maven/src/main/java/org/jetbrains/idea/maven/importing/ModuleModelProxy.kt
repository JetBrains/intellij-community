// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.containers.map2Array
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader.createEntitySourceForModule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleByEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.getInstance
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

class ModuleModelProxy(private val diff: WorkspaceEntityStorageBuilder,
                       private val project: Project) {
  private val virtualFileManager: VirtualFileUrlManager = project.getService(VirtualFileUrlManager::class.java)
  private val versionedStorage: VersionedEntityStorage

  fun disposeModule(module: Module) {
    if (module.project.isDisposed) {
      //if the project is being disposed now, removing module won't work because WorkspaceModelImpl won't fire events and the module won't be disposed
      //it looks like this may happen in tests only so it's ok to skip removal of the module since the project will be disposed anyway
      return
    }
    if (module !is ModuleBridge) return
    val moduleEntity: ModuleEntity = diff.findModuleEntity(module) ?: return //MavenProjectImporter.LOG.error("Could not find module entity to remove by $module");
    diff.removeEntity(moduleEntity)
  }

  val modules: Array<Module>
    get() = diff.entities(ModuleEntity::class.java)
      .map { diff.findModuleByEntity(it) }
      .filterNotNull()
      .toList()
      .map2Array { it }


  fun findModuleByName(name: String): Module? {
    val entity = diff.resolve(ModuleId(name)) ?: return null
    return diff.findModuleByEntity(entity)
  }

  fun newModule(path: String, moduleTypeId: String): Module {
    val systemIndependentPath = FileUtil.toSystemIndependentName(path)
    val modulePath = ModulePath(systemIndependentPath, null)
    val name = modulePath.moduleName
    val source = createEntitySourceForModule(project,
                                             virtualFileManager.fromPath(
                                               PathUtil.getParentPath(
                                                 systemIndependentPath)), ExternalProjectSystemRegistry.getInstance().getSourceById(
      ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID))
    val moduleEntity = diff.addEntity(ModifiableModuleEntity::class.java, source) {
      this.name = name
      type = moduleTypeId
      dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency)
    }
    val moduleManager = getInstance(project)
    val module = moduleManager.createModuleInstance(moduleEntity, versionedStorage, diff, true)
    diff.getMutableExternalMapping<Module>("intellij.modules.bridge").addMapping(moduleEntity, module)
    return module
  }

  fun setModuleGroupPath(module: Module, groupPath: Array<String>?) {
    if (module !is ModuleBridge) return

    val moduleEntity = diff.findModuleEntity(module) ?: error("Could not resolve module entity for $module")
    val moduleGroupEntity = moduleEntity.groupPath
    val groupPathList = groupPath?.toList()

    if (moduleGroupEntity?.path != groupPathList) {
      when {
        moduleGroupEntity == null && groupPathList != null -> diff.addModuleGroupPathEntity(
          module = moduleEntity,
          path = groupPathList,
          source = moduleEntity.entitySource
        )

        moduleGroupEntity == null && groupPathList == null -> Unit
        moduleGroupEntity != null && groupPathList == null -> diff.removeEntity(moduleGroupEntity)
        moduleGroupEntity != null && groupPathList != null -> diff.modifyEntity(ModifiableModuleGroupPathEntity::class.java,
                                                                                moduleGroupEntity) {
          path = groupPathList
        }

        else -> error("Should not be reached")
      }
    }
  }

  init {
    versionedStorage = VersionedEntityStorageOnBuilder(diff)
  }
}