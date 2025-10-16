package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.python.common.tools.ToolId

internal interface PyProjectTomlWorkspaceEntity : WorkspaceEntity {

  // [tool, probablyWorkspaceRoot?]. If root is null -> tool didn't implement workspace, just participated in this entry creation
  val participatedTools: Map<ToolId, ModuleId?>

  @Parent
  val module: ModuleEntity
}

internal val ModuleEntity.pyProjectTomlEntity: PyProjectTomlWorkspaceEntity?
  by WorkspaceEntity.extension()

