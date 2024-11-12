// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.messages.Topic
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.common.PackageManagerHolder
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.sdk.PythonSdkUpdater
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  abstract var installedPackages: List<PythonPackage>

  abstract val repositoryManager: PythonRepositoryManager

  suspend fun installPackage(specification: PythonPackageSpecification, options: List<String>): Result<List<PythonPackage>> {
    installPackageCommand(specification, options).onFailure { return Result.failure(it) }
    refreshPaths()
    return reloadPackages()
  }

  suspend fun updatePackage(specification: PythonPackageSpecification): Result<List<PythonPackage>> {
    updatePackageCommand(specification).onFailure { return Result.failure(it) }
    refreshPaths()
    return reloadPackages()
  }

  suspend fun uninstallPackage(pkg: PythonPackage): Result<List<PythonPackage>> {
    uninstallPackageCommand(pkg).onFailure { return Result.failure(it) }
    refreshPaths()
    return reloadPackages()
  }

  open suspend fun reloadPackages(): Result<List<PythonPackage>> {
    val packages = reloadPackagesCommand().getOrElse {
      return Result.failure(it)
    }

    installedPackages = packages
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
      syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }

    return Result.success(packages)
  }

  protected abstract suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<String>
  protected abstract suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<String>
  protected abstract suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<String>
  protected abstract suspend fun reloadPackagesCommand(): Result<List<PythonPackage>>

  internal suspend fun refreshPaths() {
    writeAction {
      VfsUtil.markDirtyAndRefresh(true, true, true, *sdk.rootProvider.getFiles(OrderRootType.CLASSES))
      PythonSdkUpdater.scheduleUpdate(sdk, project)
    }
  }

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      return project.service<PackageManagerHolder>().forSdk(project, sdk)
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC = Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }
}