// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

object MavenProjectsTreeEntitySource : EntitySource

interface MavenProjectsTreeSettingsEntity: WorkspaceEntity {
  val importedFilePaths: List<String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : MavenProjectsTreeSettingsEntity, WorkspaceEntity.Builder<MavenProjectsTreeSettingsEntity> {
    override var entitySource: EntitySource
    override var importedFilePaths: MutableList<String>
  }

  companion object : EntityType<MavenProjectsTreeSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(importedFilePaths: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): MavenProjectsTreeSettingsEntity {
      val builder = builder()
      builder.importedFilePaths = importedFilePaths.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: MavenProjectsTreeSettingsEntity,
                                      modification: MavenProjectsTreeSettingsEntity.Builder.() -> Unit) = modifyEntity(
  MavenProjectsTreeSettingsEntity.Builder::class.java, entity, modification)
//endregion

object MavenCustomModuleNameMappingEntitySource : EntitySource

interface MavenCustomModuleNameMappingEntity: WorkspaceEntity {
  val customModuleNames: Map<VirtualFileUrl, String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : MavenCustomModuleNameMappingEntity, WorkspaceEntity.Builder<MavenCustomModuleNameMappingEntity> {
    override var entitySource: EntitySource
    override var customModuleNames: Map<VirtualFileUrl, String>
  }

  companion object : EntityType<MavenCustomModuleNameMappingEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(customModuleNames: Map<VirtualFileUrl, String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): MavenCustomModuleNameMappingEntity {
      val builder = builder()
      builder.customModuleNames = customModuleNames
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: MavenCustomModuleNameMappingEntity,
                                      modification: MavenCustomModuleNameMappingEntity.Builder.() -> Unit) = modifyEntity(
  MavenCustomModuleNameMappingEntity.Builder::class.java, entity, modification)
//endregion
