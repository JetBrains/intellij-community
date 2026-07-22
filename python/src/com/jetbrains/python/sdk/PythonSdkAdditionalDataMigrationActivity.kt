// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class PythonSdkAdditionalDataMigrationActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    if (project.isDisposed) return

    val pythonSdks = ProjectJdkTable.getInstance().allJdks.filter { PythonSdkUtil.isPythonSdk(it) }
    writeAction {
      migratePythonSdkAdditionalData(project, pythonSdks)
    }
  }
}

@RequiresWriteLock
private fun migratePythonSdkAdditionalData(project: Project, sdks: List<Sdk>) {
  val projectSdk = ProjectRootManager.getInstance(project).projectSdk
  for (sdk in sdks) {
    val fallbackWorkingDirectory = sdk.associatedModuleNioPath
                                   ?: project.modules.firstNotNullOfOrNull { module ->
                                     module.takeIf { ModuleRootManager.getInstance(it).sdk == sdk }?.baseDir?.path?.let { Path.of(it) }
                                   }
                                   ?: project.basePath?.takeIf { projectSdk == sdk }?.let { Path.of(it) }
    val modificator = sdk.sdkModificator
    val additionalData = modificator.sdkAdditionalData as? PythonSdkAdditionalData ?: continue
    if (!additionalData.migrateAdditionalData(fallbackWorkingDirectory)) continue

    modificator.sdkAdditionalData = additionalData
    modificator.commitChanges()
  }
}
