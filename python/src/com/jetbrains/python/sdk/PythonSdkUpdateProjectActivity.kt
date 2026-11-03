// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.associatedModuleDir
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.sitePackagesDirectory
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PythonSdkUpdateProjectActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    val application = ApplicationManager.getApplication()

    val messageBusConnection = project.messageBus.connect()
    messageBusConnection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(interpreter: PythonInterpreter) {
        // Restarts the daemon when the installed-packages snapshot is (re)populated. Without
        // this, inspections that consult `listInstalledPackagesSnapshot()` (notably
        // [com.jetbrains.python.requirements.inspections.tools.RequirementInspection])
        // can run on file open before the package manager has finished its first load,
        // capture an empty snapshot, and report every declared dependency as
        // "not installed". The previously-existing `outdatedPackagesChanged` hook only
        // fires when the outdated map actually changes — for a freshly opened project with
        // zero outdated packages the map stays empty, no event fires, and the stale
        // inspection results persist until the next file edit.
        DaemonCodeAnalyzer.getInstance(project).restart("PythonSdkUpdateProjectActivity.packagesChanged")
      }

      override fun outdatedPackagesChanged(interpreter: PythonInterpreter) {
        DaemonCodeAnalyzer.getInstance(project).restart("PythonSdkUpdateProjectActivity.outdatedPackagesChanged")
      }

      override fun declaredPackagesChanged(interpreter: PythonInterpreter) {
        // The dependency inspections read the declared packages, which load after the start of the manager.
        DaemonCodeAnalyzer.getInstance(project).restart("PythonSdkUpdateProjectActivity.declaredPackagesChanged")
      }
    })


    if (application.isUnitTestMode) return
    if (dropUpdaterInHeadless()) return  // see PythonHeadlessSdkUpdater
    if (project.isDisposed) return

    for (interpreter in EvoPyProjectModel.getInstance(project).snapshot().interpreters) {
      PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = true, background = false))
    }
  }
}

internal suspend fun refreshPaths(project: Project, interpreter: PythonInterpreter): Unit = withContext(Dispatchers.IO) {
  // Background refreshing breaks structured concurrency: there is a some activity in background that locks files.
  // Temporary folders can't be deleted on Windows due to that.
  // That breaks tests.
  // This code should be deleted, but disabled temporary to fix tests
  if (!(ApplicationManager.getApplication().isUnitTestMode && SystemInfoRt.isWindows)) {
    val files = interpreter.classRoots(project)
    VfsUtil.markDirty(true, true, *files)
    RefreshQueue.getInstance().refresh(true, files.toList())
    RefreshQueue.getInstance().refresh(false, listOfNotNull(interpreter.associatedModuleDir))
  }
  else {
    RefreshQueue.getInstance().refresh(true, listOfNotNull(interpreter.sitePackagesDirectory(project), interpreter.associatedModuleDir))
  }

  PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = false, background = false))
}

internal fun dropUpdaterInHeadless(): Boolean {
  return ApplicationManager.getApplication().isHeadlessEnvironment && !Registry.`is`("ide.warmup.use.predicates")
}
