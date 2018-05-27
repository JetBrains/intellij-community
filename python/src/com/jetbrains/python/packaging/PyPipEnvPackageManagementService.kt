// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.RepoPackage
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.flavors.pipFileLockSources
import java.lang.Exception

/**
 * @author vlan
 */
class PyPipEnvPackageManagementService(project: Project, sdk: Sdk) : PyPackageManagementService(project, sdk) {
  override fun getAllRepositories(): List<String>? = null

  override fun canInstallToUser() = false

  override fun getAllPackages(): List<RepoPackage> {
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(sdk.pipFileLockSources, false)
    return allPackagesCached
  }

  override fun reloadAllPackages(): List<RepoPackage> {
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(sdk.pipFileLockSources, true)
    return allPackagesCached
  }

  override fun getAllPackagesCached(): List<RepoPackage> =
    PyPIPackageUtil.INSTANCE.getAdditionalPackages(sdk.pipFileLockSources)

  override fun installPackage(repoPackage: RepoPackage,
                              version: String?,
                              forceUpgrade: Boolean,
                              extraOptions: String?,
                              listener: Listener,
                              installToUser: Boolean) {
    val ui = PyPackageManagerUI(project, sdk, object : PyPackageManagerUI.Listener {
      override fun started() {
        listener.operationStarted(repoPackage.name)
      }

      override fun finished(exceptions: List<ExecutionException>?) {
        listener.operationFinished(repoPackage.name, toErrorDescription(exceptions, sdk))
      }
    })
    val requirement = when {
      version != null -> PyRequirement(repoPackage.name, version)
      else -> PyRequirement(repoPackage.name)
    }
    val extraArgs = extraOptions?.split(" +".toRegex()) ?: emptyList()
    ui.install(listOf(requirement), extraArgs)
  }

  override fun fetchPackageVersions(packageName: String?, consumer: CatchingConsumer<MutableList<String>, Exception>?) {
    // TODO: Override it for pipenv indices
    super.fetchPackageVersions(packageName, consumer)
  }

  override fun fetchLatestVersion(pkg: InstalledPackage, consumer: CatchingConsumer<String, Exception>) {
    // TODO: Override it for pipenv indices
    super.fetchLatestVersion(pkg, consumer)
  }
}