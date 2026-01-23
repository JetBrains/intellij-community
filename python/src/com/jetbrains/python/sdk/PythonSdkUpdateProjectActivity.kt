// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil.getSitePackagesDirectory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PythonSdkUpdateProjectActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    val application = ApplicationManager.getApplication()

    val messageBusConnection = project.messageBus.connect()
    messageBusConnection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun outdatedPackagesChanged(sdk: Sdk) {
        DaemonCodeAnalyzer.getInstance(project).restart("PythonSdkUpdateProjectActivity.outdatedPackagesChanged")
      }
    })


    if (application.isUnitTestMode) return
    if (dropUpdaterInHeadless()) return  // see PythonHeadlessSdkUpdater
    if (project.isDisposed) return

    for (sdk in PythonSdkUpdater.getPythonSdks(project)) {
      PythonSdkUpdater.scheduleUpdate(sdk, project)
    }
  }
}

@ApiStatus.Internal
suspend fun refreshPaths(project: Project, sdk: Sdk, reason: Any) {
  // Background refreshing breaks structured concurrency: there is a some activity in background that locks files.
  // Temporary folders can't be deleted on Windows due to that.
  // That breaks tests.
  // This code should be deleted, but disabled temporary to fix tests
  if (!(ApplicationManager.getApplication().isUnitTestMode && SystemInfoRt.isWindows)) {
    val files = sdk.rootProvider.getFiles(OrderRootType.CLASSES)
    VfsUtil.markDirty(true, true, *files)
    RefreshQueue.getInstance().refresh(true, files.toList())
    RefreshQueue.getInstance().refresh(false, listOfNotNull(sdk.associatedModuleDir))
  }
  else {
    RefreshQueue.getInstance().refresh(true, listOfNotNull(getSitePackagesDirectory(sdk), sdk.associatedModuleDir))
  }

  //Restart all inspections because packages are changed
  DaemonCodeAnalyzer.getInstance(project).restart(reason)
  PythonSdkUpdater.scheduleUpdate(sdk, project, false)
}

internal fun dropUpdaterInHeadless(): Boolean {
  return ApplicationManager.getApplication().isHeadlessEnvironment && !Registry.`is`("ide.warmup.use.predicates")
}