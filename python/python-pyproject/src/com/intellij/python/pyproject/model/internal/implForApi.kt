package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun suggestSdkImpl(module: Module): SuggestedSdk? = withContext(Dispatchers.Default) {
  val entity = module.findModuleEntity()?.pyProjectTomlEntity ?: return@withContext null
  val storage = module.project.workspaceModel.currentSnapshot
  val toolWithWorkspace = entity.participatedTools.firstNotNullOfOrNull { (tool, moduleId) ->
    val module = moduleId?.resolve(storage)?.findModule(storage)
    if (module != null) Pair(tool, module) else null
  }
  if (toolWithWorkspace != null) {
    val (tool, module) = toolWithWorkspace
    SuggestedSdk.SameAs(module, tool)
  }
  else {
    val tools = entity.participatedTools.keys
    val dirWithToml = entity.dirWithToml
    val dirWithTomlPath = (dirWithToml.virtualFile
                           ?: error("Can't find dir for $dirWithToml . Directory might already be deleted. Try to restart IDE")
                          ).toNioPath()
    SuggestedSdk.PyProjectIndependent(preferTools = tools, moduleDir = dirWithTomlPath)
  }
}

