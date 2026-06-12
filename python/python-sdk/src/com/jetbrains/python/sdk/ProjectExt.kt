// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Renames the SDK currently registered as [oldName] to [newName] and keeps this project's references pointing at it.
 *
 * Returns a failure if [newName] is already used by another SDK; on success the project SDK and every module SDK dependency that
 * referenced the SDK keep pointing at it.
 *
 * Renaming the SDK fires `jdkNameChanged`. When the SDK is referenced by BOTH the project SDK and a module SDK dependency, two
 * listeners (`ProjectRootManagerImpl` + `ModuleDependencyIndexImpl`) each call `updateProjectModel`, and the multiverse context
 * provider re-pumps the message bus between them, producing a recursive `updateProjectModel` that aborts the reference rewrite and
 * drops the interpreter's association (PY-88229). To avoid that, the project SDK reference is detached for the duration of the rename
 * so only one listener updates the project model; it is always restored afterwards (even if the rename fails), so the project never
 * ends up without an interpreter. The project SDK and explicit module SDK references are then re-pointed to the renamed SDK.
 */
@Internal
@RequiresWriteLock
fun Project.renameSdk(oldName: String, newName: String): PyResult<Unit> {
  val jdkTable = ProjectJdkTable.getInstance()
  val sdk = jdkTable.findJdk(oldName)
            ?: return PyResult.localizedError(message("python.sdk.rename.interpreter.not.found", oldName))

  if (oldName == newName) {
    return PyResult.success(Unit)
  }

  if (jdkTable.findJdk(newName) != null) {
    return PyResult.localizedError(message("python.sdk.rename.interpreter.name.already.exists", newName))
  }


  val projectRootManager = ProjectRootManager.getInstance(this)
  // Capture modules whose explicit (non-inherited) SDK is this one; inherited modules follow the project SDK and need no action.
  val modulesWithExplicitSdk = ModuleManager.getInstance(this).modules.filter { module ->
    val rootManager = ModuleRootManager.getInstance(module)
    !rootManager.isSdkInherited && rootManager.sdk === sdk
  }
  val isProjectSdk = projectRootManager.projectSdk?.name == oldName
  if (isProjectSdk) {
    projectRootManager.projectSdk = null
  }

  try {
    sdk.sdkModificator.let {
      it.name = newName
      it.commitChanges()
    }
  }
  finally {
    // Restore the project SDK reference: the renamed SDK on success, the unchanged one if the rename failed.
    if (isProjectSdk) {
      projectRootManager.projectSdk = sdk
    }
  }

  // Re-point the explicit module references to the SDK, which is now renamed in place.
  for (module in modulesWithExplicitSdk) {
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
  }
  return Result.success(Unit)
}
