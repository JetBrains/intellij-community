// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.messages.Topic
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.sdk.PythonSdkUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  abstract var installedPackages: List<PythonPackage>

  abstract val repositoryManager: PythonRepositoryManager

  suspend fun installPackage(
    spec: PythonPackageSpecification,
    options: List<String> = emptyList(),
    withBackgroundProgress: Boolean,
  ): Result<List<PythonPackage>> = installPackages(listOf(spec), options, withBackgroundProgress)

  suspend fun installPackages(
    packages: List<PythonPackageSpecification>,
    options: List<String> = emptyList(),
    withBackgroundProgress: Boolean,
  ): Result<List<PythonPackage>> {
    return if (withBackgroundProgress)
      installPackagesWithBackgroundProcess(packages, options)
    else
      installPackagesSilently(packages, options)
  }

  suspend fun updatePackage(specification: PythonPackageSpecification): Result<List<PythonPackage>> {
    updatePackageCommand(specification).onFailure {
      return Result.failure(it)
    }
    refreshPaths()
    return reloadPackages()
  }

  suspend fun uninstallPackage(pkg: PythonPackage): Result<List<PythonPackage>> {
    thisLogger().info("Uninstall package $pkg: start")
    uninstallPackageCommand(pkg).onFailure { return Result.failure(it) }
    thisLogger().info("Uninstall package $pkg: finished")
    refreshPaths()
    return reloadPackages()
  }

  open suspend fun reloadPackages(): Result<List<PythonPackage>> {
    thisLogger().info("Reload packages: start")
    val packages = reloadPackagesCommand().getOrElse {
      return Result.failure(it)
    }
    thisLogger().info("Reload packages: finish")

    installedPackages = packages
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
      syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }

    return Result.success(packages)
  }

  fun packageExists(pkg: PythonPackage): Boolean = installedPackages.any { it.name.equals(pkg.name, ignoreCase = true) }

  abstract suspend fun installPackageCommand(specification: PythonPackageSpecification, options: List<String>): Result<Unit>
  protected abstract suspend fun updatePackageCommand(specification: PythonPackageSpecification): Result<Unit>
  protected abstract suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit>
  protected abstract suspend fun reloadPackagesCommand(): Result<List<PythonPackage>>

  internal suspend fun refreshPaths() {
    edtWriteAction {
      // Background refreshing breaks structured concurrency: there is a some activity in background that locks files.
      // Temporary folders can't be deleted on Windows due to that.
      // That breaks tests.
      // This code should be deleted, but disabled temporary to fix tests
      if (!(ApplicationManager.getApplication().isUnitTestMode && SystemInfoRt.isWindows)) {
        VfsUtil.markDirtyAndRefresh(true, true, true, *sdk.rootProvider.getFiles(OrderRootType.CLASSES))
      }
      PythonSdkUpdater.scheduleUpdate(sdk, project)
    }
  }

  private suspend fun installPackagesWithBackgroundProcess(packages: List<PythonPackageSpecification>, options: List<String> = emptyList()): Result<List<PythonPackage>> {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.installing.packages")
    }
    else {
      PyBundle.message("python.packaging.installing.package", packages.first().name)
    }

    return withBackgroundProgress(project = project, progressTitle, cancellable = true) {
      reportSequentialProgress(packages.size) { reporter ->
        packages.forEach { specification ->
          reporter.itemStep(PyBundle.message("python.packaging.installing.package", specification.name)) {
            installPackageInternal(specification, options)
          }.onFailure { return@withBackgroundProgress Result.failure(it) }
        }
      }

      refreshPaths()
      reloadPackages()
    }
  }

  private suspend fun installPackagesSilently(
    specifications: List<PythonPackageSpecification>,
    options: List<String>,
  ): Result<List<PythonPackage>> {
    specifications.forEach { specification ->
      val installResult = installPackageInternal(specification, options)
      installResult.onFailure {
        return Result.failure(it)
      }
    }

    refreshPaths()
    return reloadPackages()
  }


  private suspend fun installPackageInternal(specification: PythonPackageSpecification, options: List<String>): Result<Unit> {
    val result = runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.new.project.install.failed.title", specification.name), specification.name) {
      installPackageCommand(specification, options)
    }
    result.onFailure {
      thisLogger().info("install $specification: error. Output: \n${it.stackTraceToString()}")
      return Result.failure(it)
    }
    result.onSuccess {
      thisLogger().info("install $specification: success")
      return Result.success(Unit)
    }
    return result
  }

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      val pythonPackageManagerService = project.service<PythonPackageManagerService>()
      val manager = pythonPackageManagerService.forSdk(project, sdk)
      pythonPackageManagerService.getServiceScope().launch(Dispatchers.IO) {
        manager.repositoryManager.initCaches()
      }
      return manager
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC: Topic<PythonPackageManagementListener> = Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
    val RUNNING_PACKAGING_TASKS: Key<Boolean> = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks")
  }
}