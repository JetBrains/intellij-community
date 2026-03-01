// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MavenProjectsTreeSettingsEntityModifications")

package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface MavenProjectsTreeSettingsEntityBuilder : WorkspaceEntityBuilder<MavenProjectsTreeSettingsEntity> {
  override var entitySource: EntitySource
  var importedFilePaths: MutableList<String>
}

internal object MavenProjectsTreeSettingsEntityType :
  EntityType<MavenProjectsTreeSettingsEntity, MavenProjectsTreeSettingsEntityBuilder>() {
  override val entityClass: Class<MavenProjectsTreeSettingsEntity> get() = MavenProjectsTreeSettingsEntity::class.java
  operator fun invoke(
    importedFilePaths: List<String>,
    entitySource: EntitySource,
    init: (MavenProjectsTreeSettingsEntityBuilder.() -> Unit)? = null,
  ): MavenProjectsTreeSettingsEntityBuilder {
    val builder = builder()
    builder.importedFilePaths = importedFilePaths.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyMavenProjectsTreeSettingsEntity(
  entity: MavenProjectsTreeSettingsEntity,
  modification: MavenProjectsTreeSettingsEntityBuilder.() -> Unit,
): MavenProjectsTreeSettingsEntity = modifyEntity(MavenProjectsTreeSettingsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createMavenProjectsTreeSettingsEntity")
fun MavenProjectsTreeSettingsEntity(
  importedFilePaths: List<String>,
  entitySource: EntitySource,
  init: (MavenProjectsTreeSettingsEntityBuilder.() -> Unit)? = null,
): MavenProjectsTreeSettingsEntityBuilder = MavenProjectsTreeSettingsEntityType(importedFilePaths, entitySource, init)
