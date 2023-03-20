// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

object MavenProjectsTreeEntitySource : EntitySource

interface MavenProjectsTreeSettingsEntity: WorkspaceEntity {
  val importedFilePaths: List<String>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : MavenProjectsTreeSettingsEntity, WorkspaceEntity.Builder<MavenProjectsTreeSettingsEntity>, ObjBuilder<MavenProjectsTreeSettingsEntity> {
    override var entitySource: EntitySource
    override var importedFilePaths: MutableList<String>
  }

  companion object : Type<MavenProjectsTreeSettingsEntity, Builder>() {
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
