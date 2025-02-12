// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.pipenv

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.pipenv.pipFileLockSources

class PyPipEnvPackageManagementService(project: Project, sdk: Sdk) : PyPackageManagementService(project, sdk) {
  override fun getAllRepositories(): List<String>? = null

  override fun canInstallToUser() = false

  @RequiresBackgroundThread
  override fun getAllPackages(): List<RepoPackage> {
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(runBlockingCancellable { pipFileLockSources(sdk) }, false)
    return allPackagesCached
  }

  @RequiresBackgroundThread
  override fun reloadAllPackages(): List<RepoPackage> {
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(runBlockingCancellable { pipFileLockSources(sdk) }, true)
    return allPackagesCached
  }

  @RequiresBackgroundThread
  override fun getAllPackagesCached(): List<RepoPackage> =
    PyPIPackageUtil.INSTANCE.getAdditionalPackages(runBlockingCancellable { pipFileLockSources(sdk) })

  override fun installPackage(
    repoPackage: RepoPackage,
    version: String?,
    forceUpgrade: Boolean,
    extraOptions: String?,
    listener: Listener,
    installToUser: Boolean,
  ) {
    val ui = PyPackageManagerUI(project, sdk, object : PyPackageManagerUI.Listener {
      override fun started() {
        listener.operationStarted(repoPackage.name)
      }

      override fun finished(exceptions: List<ExecutionException>?) {
        listener.operationFinished(repoPackage.name, toErrorDescription(exceptions, sdk))
      }
    })
    val requirement = when {
                        version != null -> PyRequirementParser.fromLine("${repoPackage.name}==$version")
                        else -> PyRequirementParser.fromLine(repoPackage.name)
                      } ?: return
    val extraArgs = extraOptions?.split(" +".toRegex()) ?: emptyList()
    ui.install(listOf(requirement), extraArgs)
  }

}