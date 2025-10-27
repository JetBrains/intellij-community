@file:JvmName("PyProjectTomlWorkspaceEntityModifications")

package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.python.common.tools.ToolId

@GeneratedCodeApiVersion(3)
internal interface PyProjectTomlWorkspaceEntityBuilder : WorkspaceEntityBuilder<PyProjectTomlWorkspaceEntity> {
  override var entitySource: EntitySource
  var participatedTools: Map<ToolId, ModuleId?>
  var module: ModuleEntityBuilder
}

internal object PyProjectTomlWorkspaceEntityType : EntityType<PyProjectTomlWorkspaceEntity, PyProjectTomlWorkspaceEntityBuilder>() {
  override val entityClass: Class<PyProjectTomlWorkspaceEntity> get() = PyProjectTomlWorkspaceEntity::class.java
  operator fun invoke(
    participatedTools: Map<ToolId, ModuleId?>,
    entitySource: EntitySource,
    init: (PyProjectTomlWorkspaceEntityBuilder.() -> Unit)? = null,
  ): PyProjectTomlWorkspaceEntityBuilder {
    val builder = builder()
    builder.participatedTools = participatedTools
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

internal fun MutableEntityStorage.modifyPyProjectTomlWorkspaceEntity(
  entity: PyProjectTomlWorkspaceEntity,
  modification: PyProjectTomlWorkspaceEntityBuilder.() -> Unit,
): PyProjectTomlWorkspaceEntity = modifyEntity(PyProjectTomlWorkspaceEntityBuilder::class.java, entity, modification)

internal var ModuleEntityBuilder.pyProjectTomlEntity: PyProjectTomlWorkspaceEntityBuilder?
  by WorkspaceEntity.extensionBuilder(PyProjectTomlWorkspaceEntity::class.java)


@JvmOverloads
@JvmName("createPyProjectTomlWorkspaceEntity")
internal fun PyProjectTomlWorkspaceEntity(
  participatedTools: Map<ToolId, ModuleId?>,
  entitySource: EntitySource,
  init: (PyProjectTomlWorkspaceEntityBuilder.() -> Unit)? = null,
): PyProjectTomlWorkspaceEntityBuilder = PyProjectTomlWorkspaceEntityType(participatedTools, entitySource, init)
