// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.workspaceBridge

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.common.tools.ToolId
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
@ApiStatus.Internal
data class ToolWorkspaceLayout(
  val rootModule: Module,
  val memberModules: List<Module>,
)

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
  return ToolWorkspaceLayout(rootModule, members)
}
