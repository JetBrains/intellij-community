package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val Module.isPyProjectTomlBasedImpl: Boolean get() = findModuleEntity()?.pyProjectTomlEntity != null

internal suspend fun suggestSdkImpl(module: Module): SuggestedSdk? = withContext(Dispatchers.Default) {
  val entity = module.findModuleEntity()?.pyProjectTomlEntity ?: return@withContext null
  val storage = module.project.workspaceModel.currentSnapshot
  val moduleId = module.findModuleEntity(storage)?.symbolicId
  if (moduleId == null) {
    logger.warn("Module $module doesn't exist in a storage, no SDK could be suggested")
    return@withContext null
  }
  val toolWithWorkspace = entity.participatedTools.firstNotNullOfOrNull { (tool, workspaceRootModuleId) ->
    if (workspaceRootModuleId == moduleId) {
      null // This module is a workspace root and can't be `SameAs()` itself
    }
    else {
      val workspaceRootModule = workspaceRootModuleId?.resolve(storage)?.findModule(storage)
      if (workspaceRootModule != null) {
        Pair(tool, workspaceRootModule)
      }
      else {
        null
      }
    }
  }
  if (toolWithWorkspace != null) {
    val (tool, workspaceRootModule) = toolWithWorkspace
    assert(workspaceRootModule != module) { "$module is a workspace root, can't point to itself" }
    SuggestedSdk.SameAs(workspaceRootModule, tool)
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

private val logger = fileLogger()