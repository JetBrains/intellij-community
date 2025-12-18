@file:JvmName("PyProjectTomlWorkspaceEntityModifications")

package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.common.tools.ToolId

@GeneratedCodeApiVersion(3)
interface PyProjectTomlWorkspaceEntityBuilder : WorkspaceEntityBuilder<PyProjectTomlWorkspaceEntity> {
  override var entitySource: EntitySource
  var participatedTools: Map<ToolId, ModuleId?>
  var dirWithToml: VirtualFileUrl
  var module: ModuleEntityBuilder
}

internal object PyProjectTomlWorkspaceEntityType : EntityType<PyProjectTomlWorkspaceEntity, PyProjectTomlWorkspaceEntityBuilder>() {
  override val entityClass: Class<PyProjectTomlWorkspaceEntity> get() = PyProjectTomlWorkspaceEntity::class.java
  operator fun invoke(
    participatedTools: Map<ToolId, ModuleId?>,
    dirWithToml: VirtualFileUrl,
    entitySource: EntitySource,
    init: (PyProjectTomlWorkspaceEntityBuilder.() -> Unit)? = null,
  ): PyProjectTomlWorkspaceEntityBuilder {
    val builder = builder()
    builder.participatedTools = participatedTools
    builder.dirWithToml = dirWithToml
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyPyProjectTomlWorkspaceEntity(
  entity: PyProjectTomlWorkspaceEntity,
  modification: PyProjectTomlWorkspaceEntityBuilder.() -> Unit,
): PyProjectTomlWorkspaceEntity = modifyEntity(PyProjectTomlWorkspaceEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.pyProjectTomlEntity: PyProjectTomlWorkspaceEntityBuilder?
  by WorkspaceEntity.extensionBuilder(PyProjectTomlWorkspaceEntity::class.java)


@JvmOverloads
@JvmName("createPyProjectTomlWorkspaceEntity")
fun PyProjectTomlWorkspaceEntity(
  participatedTools: Map<ToolId, ModuleId?>,
  dirWithToml: VirtualFileUrl,
  entitySource: EntitySource,
  init: (PyProjectTomlWorkspaceEntityBuilder.() -> Unit)? = null,
): PyProjectTomlWorkspaceEntityBuilder = PyProjectTomlWorkspaceEntityType(participatedTools, dirWithToml, entitySource, init)
