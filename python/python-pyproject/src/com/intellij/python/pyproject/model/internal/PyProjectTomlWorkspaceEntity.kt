package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.jetbrains.python.ToolId

internal interface PyProjectTomlWorkspaceEntity : WorkspaceEntity {

  // [tool, probablyWorkspaceRoot?]. If root is null -> tool didn't implement workspace, just participated in this entry creation
  val participatedTools: Map<ToolId, ModuleId?>

  @Parent
  val module: ModuleEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<PyProjectTomlWorkspaceEntity> {
    override var entitySource: EntitySource
    var participatedTools: Map<ToolId, ModuleId?>
    var module: ModuleEntity.Builder
  }

  companion object : EntityType<PyProjectTomlWorkspaceEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      participatedTools: Map<ToolId, ModuleId?>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.participatedTools = participatedTools
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
internal fun MutableEntityStorage.modifyPyProjectTomlWorkspaceEntity(
  entity: PyProjectTomlWorkspaceEntity,
  modification: PyProjectTomlWorkspaceEntity.Builder.() -> Unit,
): PyProjectTomlWorkspaceEntity = modifyEntity(PyProjectTomlWorkspaceEntity.Builder::class.java, entity, modification)

internal var ModuleEntity.Builder.pyProjectTomlEntity: PyProjectTomlWorkspaceEntity.Builder?
  by WorkspaceEntity.extensionBuilder(PyProjectTomlWorkspaceEntity::class.java)
//endregion

internal val ModuleEntity.pyProjectTomlEntity: PyProjectTomlWorkspaceEntity?
  by WorkspaceEntity.extension()

