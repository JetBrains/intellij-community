// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

object MavenProjectsTreeEntitySource : EntitySource

interface MavenProjectsTreeSettingsEntity: WorkspaceEntity {
  val importedFilePaths: List<String>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<MavenProjectsTreeSettingsEntity> {
    override var entitySource: EntitySource
    var importedFilePaths: MutableList<String>
  }

  companion object : EntityType<MavenProjectsTreeSettingsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      importedFilePaths: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
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
fun MutableEntityStorage.modifyMavenProjectsTreeSettingsEntity(
  entity: MavenProjectsTreeSettingsEntity,
  modification: MavenProjectsTreeSettingsEntity.Builder.() -> Unit,
): MavenProjectsTreeSettingsEntity {
  return modifyEntity(MavenProjectsTreeSettingsEntity.Builder::class.java, entity, modification)
}
//endregion
