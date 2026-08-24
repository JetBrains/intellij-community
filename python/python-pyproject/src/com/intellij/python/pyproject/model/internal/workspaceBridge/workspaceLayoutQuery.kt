// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.community.common.tools.ToolId
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus

/**
 * Workspace layout for a given tool.
 *
 * Note: a workspace with zero members (root-only, no sub-projects yet) is not
 * distinguished from a non-workspace project — both return `null` from
 * [getToolWorkspaceLayout]. This is acceptable for packaging purposes.
 */
@ConsistentCopyVisibility
@ApiStatus.Internal
data class ToolWorkspaceLayout internal constructor(
  val tool: ToolId,
  val rootModule: Module,
  val memberModules: List<Module>,
) {
  /** Every module of the workspace: its root followed by its members. */
  val allModules: List<Module> get() = listOf(rootModule) + memberModules
}

/**
 * Returns workspace layout for the given tool if this module participates in a workspace.
 * Works regardless of whether this module is the root or a member.
 * Returns null if the module doesn't participate in a workspace for this tool.
 */
@ApiStatus.Internal
fun Module.getToolWorkspaceLayout(toolId: ToolId): ToolWorkspaceLayout? {
  val storage = project.workspaceModel.currentSnapshot
  val myModuleEntity = findModuleEntity(storage) ?: return null
  val myEntity = myModuleEntity.pyProjectTomlEntity ?: return null

  if (toolId !in myEntity.participatedTools) return null

  val workspaceRootModuleId = myEntity.participatedTools[toolId]
  val rootModuleId = workspaceRootModuleId ?: myModuleEntity.symbolicId

  val rootModuleEntity = rootModuleId.resolve(storage) ?: return null
  val rootModule = rootModuleEntity.findModule(storage) ?: return null

  val members = storage.entities<ModuleEntity>()
    .filter { entity ->
      val pyEntity = entity.pyProjectTomlEntity ?: return@filter false
      pyEntity.participatedTools[toolId] == rootModuleId && entity.symbolicId != rootModuleId
    }
    .mapNotNull { it.findModule(storage) }
    .toList()

  // A workspace with zero members (root-only, no sub-projects added yet) is
  // indistinguishable from a plain non-workspace project here; both return null.
  if (members.isEmpty()) return null
  return ToolWorkspaceLayout(toolId, rootModule, members)
}

/**
 * Whether [this] workspace-model change can have altered any module's [ToolWorkspaceLayout].
 *
 * A layout is read off [PyProjectTomlWorkspaceEntity.participatedTools], which is internal to this module — so a caller
 * that caches layouts cannot watch that entity itself and has to ask here instead. Membership changes on their own:
 * dropping a member from `[tool.uv.workspace]` and putting it back leaves the same modules with the same content roots,
 * so nothing but this entity moves and a cache keyed on the module set would never notice.
 */
@ApiStatus.Internal
fun VersionedStorageChange.affectsWorkspaceLayout(): Boolean =
  getChanges(PyProjectTomlWorkspaceEntity::class.java).isNotEmpty()

/**
 * Workspace layout for the first tool this module participates in, whether as the root or as a member; `null` when it
 * is in no workspace at all.
 *
 * Tool-agnostic counterpart of [getToolWorkspaceLayout], for callers (the interpreter widget) that care only that the
 * module shares a workspace — not which tool declares it. A module participating in two tools' workspaces at once is
 * not a configuration we support, so the first tool wins.
 */
@ApiStatus.Internal
fun Module.getWorkspaceLayout(): ToolWorkspaceLayout? {
  val storage = project.workspaceModel.currentSnapshot
  val entity = findModuleEntity(storage)?.pyProjectTomlEntity ?: return null
  return entity.participatedTools.keys.firstNotNullOfOrNull { getToolWorkspaceLayout(it) }
}
