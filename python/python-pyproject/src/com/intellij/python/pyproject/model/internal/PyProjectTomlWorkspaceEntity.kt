package com.intellij.python.pyproject.model.internal

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.common.tools.ToolId

interface PyProjectTomlWorkspaceEntity : WorkspaceEntity {

  // [tool, probablyWorkspaceRoot?]. If root is null -> tool didn't implement workspace, just participated in this entry creation
  val participatedTools: Map<ToolId, ModuleId?>

  val dirWithToml: VirtualFileUrl

  @Parent
  val module: ModuleEntity
}

val ModuleEntity.pyProjectTomlEntity: PyProjectTomlWorkspaceEntity?
  by WorkspaceEntity.extension()

