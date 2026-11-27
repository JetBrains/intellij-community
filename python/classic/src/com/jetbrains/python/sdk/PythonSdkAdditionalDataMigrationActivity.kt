// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class PythonSdkAdditionalDataMigrationActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    if (project.isDisposed) return

    @Suppress("UnsafeOpenServiceCast")
    (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()
    if (project.isDisposed) return

    val pythonSdks = ProjectJdkTable.getInstance().allJdks.filter { PythonSdkUtil.isPythonSdk(it) }
    if (pythonSdks.isEmpty()) return

    // The read of a module SDK searches the SDK table, so the modules are read one time, and not under the write lock.
    val moduleDirs = if (pythonSdks.any { it.associatedModuleNioPath == null }) {
      readAction { moduleDirsBySdk(project) }
    }
    else {
      emptyMap()
    }
    writeAction {
      migratePythonSdkAdditionalData(project, pythonSdks, moduleDirs)
    }
  }
}

/** The base directory of the first module that uses each SDK. */
@RequiresReadLock
private fun moduleDirsBySdk(project: Project): Map<Sdk, VirtualFile> {
  val result = HashMap<Sdk, VirtualFile>()
  for (module in project.modules) {
    val sdk = ModuleRootManager.getInstance(module).sdk ?: continue
    if (sdk in result) continue
    result[sdk] = module.baseDir ?: continue
  }
  return result
}

@RequiresWriteLock
private fun migratePythonSdkAdditionalData(project: Project, sdks: List<Sdk>, moduleDirs: Map<Sdk, VirtualFile>) {
  val projectSdk = ProjectRootManager.getInstance(project).projectSdk
  for (sdk in sdks) {
    val fallbackWorkingDirectory = sdk.associatedModuleNioPath
                                   ?: moduleDirs[sdk]?.path?.let { Path.of(it) }
                                   ?: project.basePath?.takeIf { projectSdk == sdk }?.let { Path.of(it) }
    val modificator = sdk.sdkModificator
    val additionalData = modificator.sdkAdditionalData as? PythonSdkAdditionalData ?: continue
    if (!additionalData.migrateAdditionalData(fallbackWorkingDirectory)) continue

    modificator.sdkAdditionalData = additionalData
    modificator.commitChanges()
  }
}
