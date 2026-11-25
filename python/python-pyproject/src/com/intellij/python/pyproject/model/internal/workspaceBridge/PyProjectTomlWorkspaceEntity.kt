package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.community.common.tools.ToolId

/**
 * What the `pyproject.toml` model knows about the module of one `pyproject.toml`. The model writes it on each rebuild.
 *
 * Outside PyCharm the module stays the one of the user, see [modelOwnsModules]. The model then adds only what the
 * module lacks. It records the source roots that it added in [addedSourceRoots], so it removes only those.
 */
internal interface PyProjectTomlWorkspaceEntity : WorkspaceEntity {

  // [tool, probablyWorkspaceRoot?]. If root is null -> tool didn't implement workspace, just participated in this entry creation
  val participatedTools: Map<ToolId, ModuleId?>

  val dirWithToml: VirtualFileUrl

  /** The names of the modules that the `pyproject.toml` depends on. */
  val dependencies: List<String>

  /** The source roots that the `pyproject.toml` gives. */
  val sourceRoots: List<VirtualFileUrl>

  /**
   * The source roots that the model added. One that the user then removed stays here while the `pyproject.toml` declares
   * it, so the model does not add it again. Empty in PyCharm.
   */
  val addedSourceRoots: List<VirtualFileUrl>

  @Parent
  val module: ModuleEntity
}

internal val ModuleEntity.pyProjectTomlEntity: PyProjectTomlWorkspaceEntity?
  by WorkspaceEntity.extension()
