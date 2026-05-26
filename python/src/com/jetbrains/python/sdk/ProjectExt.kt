// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonSdkModuleRoots")

package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.ApiStatus

private val thisLogger = fileLogger()

/**
 * Returns the union of every module's source and content roots for the receiver project.
 *
 * Awaits workspace-model synchronization with the on-disk JPS model first (PY-86494), then
 * reads modules under a read action.
 *
 * Logs `LOG.warn` when the project has no modules at all (a legitimate codepath — e.g. the SDK
 * creation wizard running before any module is attached). Logs `LOG.error` for every Python
 * module that exposes no source/content roots — a state only reachable when the workspace
 * model is out of sync with JPS even after the await.
 */
@ApiStatus.Internal
suspend fun Project.getModuleRoots(): Set<VirtualFile> {
  (workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()
  return readAction {
    val modules = ModuleManager.getInstance(this).modules
    if (modules.isEmpty()) {
      thisLogger.warn("SAFETY NET: no modules in project '$name'.")
    }

    modules.flatMapTo(HashSet()) { module ->
      PyUtil.getSourceRoots(module).also { roots ->
        if (roots.isEmpty() && module.moduleTypeName == PyNames.PYTHON_MODULE_ID) {
          thisLogger.error("SAFETY NET: no roots in python module '${module.name}' of project '$name'.")
        }
      }
    }
  }
}

/**
 * Blocking bridge for Java callers and other non-suspend contexts. Must be called from a
 * background thread; internally delegates to [getModuleRoots] via [runBlockingMaybeCancellable].
 */
@ApiStatus.Internal
@RequiresBackgroundThread
@RequiresBlockingContext
fun getModuleRootsBlocking(project: Project): Set<VirtualFile> = runBlockingMaybeCancellable { project.getModuleRoots() }
