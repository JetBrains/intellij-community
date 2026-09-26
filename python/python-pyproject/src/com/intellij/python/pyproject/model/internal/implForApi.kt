package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntityIfNotDisposed
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val Module.isPyProjectTomlBasedImpl: Boolean get() = findModuleEntityIfNotDisposed().pyProjectTomlEntity != null

internal suspend fun Module.getPyProjectTomlFileImpl(): VirtualFile? = readAction {
  val entity = findModuleEntityIfNotDisposed().pyProjectTomlEntity ?: return@readAction null
  val dir = entity.dirWithToml.virtualFile ?: return@readAction null
  dir.findChild(PY_PROJECT_TOML)
}

internal suspend fun suggestSdkImpl(module: Module): SuggestedSdk? = withContext(Dispatchers.Default) {
  val moduleEntity = module.findModuleEntityIfNotDisposed()
  val pyProjectEntity = moduleEntity.pyProjectTomlEntity ?: return@withContext null

  val moduleId = moduleEntity.symbolicId

  val storage = module.project.workspaceModel.currentSnapshot
  val toolWithWorkspace = pyProjectEntity.participatedTools.firstNotNullOfOrNull { (tool, workspaceRootModuleId) ->
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
    val tools = pyProjectEntity.participatedTools.keys
    val dirWithToml = pyProjectEntity.dirWithToml.toPath()
    SuggestedSdk.PyProjectIndependent(preferTools = tools, moduleDir = dirWithToml)
  }
}

