// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.ui.pyMayBeModalBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.pathString

internal fun getBasePythonsPaths(): List<@NlsSafe String> =
  pyMayBeModalBlocking(ModalTaskOwner.guess()) {
    SystemPythonService().findSystemPythons().map { withContext(Dispatchers.IO) { it.pythonBinary.toRealPath() }.pathString }.sorted()
  }


/**
 * The SDK's owning module and the roots to treat as project-local when classifying its sys.path entries (PY-86494).
 *
 * The SDK records its owning module directory at venv/uv creation. This finds the module that has that directory as
 * a content root — from each open project's workspace-model snapshot, by URL, no indexes — and returns it with the
 * module's content + source roots. When the owner is not open, the module is `null` and only its directory is
 * returned (still enough to keep project-local paths out of the SDK's CLASSES roots). When the SDK has no
 * association, both are empty.
 */
internal fun findSdkOwnerModuleAndRoots(sdk: Sdk): Pair<Module?, Set<VirtualFile>> {
  val associatedModuleDir = sdk.associatedModuleDir ?: return null to emptySet()
  for (project in ProjectManager.getInstance().openProjects) {
    if (project.isDisposed) continue
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    for (entity in snapshot.entities<ModuleEntity>()) {
      if (entity.contentRoots.none { it.url.url == associatedModuleDir.url }) continue
      val module = entity.findModule(snapshot) ?: continue
      val roots = entity.contentRoots
        .flatMap { listOf(it.url) + it.sourceRoots.map { sr -> sr.url } }
        .mapNotNullTo(HashSet()) { it.virtualFile }
      return module to roots
    }
  }
  return null to setOf(associatedModuleDir)
}
