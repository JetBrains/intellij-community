package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.python.common.tools.ToolId

@GeneratedCodeApiVersion(3)
internal interface ModifiablePyProjectTomlWorkspaceEntity : ModifiableWorkspaceEntity<PyProjectTomlWorkspaceEntity> {
  override var entitySource: EntitySource
  var participatedTools: Map<ToolId, ModuleId?>
  var module: ModifiableModuleEntity
}

internal object PyProjectTomlWorkspaceEntityType : EntityType<PyProjectTomlWorkspaceEntity, ModifiablePyProjectTomlWorkspaceEntity>() {
  override val entityClass: Class<PyProjectTomlWorkspaceEntity> get() = PyProjectTomlWorkspaceEntity::class.java
  operator fun invoke(
    participatedTools: Map<ToolId, ModuleId?>,
    entitySource: EntitySource,
    init: (ModifiablePyProjectTomlWorkspaceEntity.() -> Unit)? = null,
  ): ModifiablePyProjectTomlWorkspaceEntity {
    val builder = builder()
    builder.participatedTools = participatedTools
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

internal fun MutableEntityStorage.modifyPyProjectTomlWorkspaceEntity(
  entity: PyProjectTomlWorkspaceEntity,
  modification: ModifiablePyProjectTomlWorkspaceEntity.() -> Unit,
): PyProjectTomlWorkspaceEntity = modifyEntity(ModifiablePyProjectTomlWorkspaceEntity::class.java, entity, modification)

internal var ModifiableModuleEntity.pyProjectTomlEntity: ModifiablePyProjectTomlWorkspaceEntity?
  by WorkspaceEntity.extensionBuilder(PyProjectTomlWorkspaceEntity::class.java)


@JvmOverloads
@JvmName("createPyProjectTomlWorkspaceEntity")
internal fun PyProjectTomlWorkspaceEntity(
  participatedTools: Map<ToolId, ModuleId?>,
  entitySource: EntitySource,
  init: (ModifiablePyProjectTomlWorkspaceEntity.() -> Unit)? = null,
): ModifiablePyProjectTomlWorkspaceEntity = PyProjectTomlWorkspaceEntityType(participatedTools, entitySource, init)
