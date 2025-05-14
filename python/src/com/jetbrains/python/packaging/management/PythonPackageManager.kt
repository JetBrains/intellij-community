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
import com.jetbrains.python.packaging.common.*
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.PythonSdkUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal

sealed class PythonPackageInstallRequest(val title: String) {
  data object AllRequirements : PythonPackageInstallRequest("All Requirements")
  data class ByLocation(val location: URI) : PythonPackageInstallRequest(location.toString())
  data class ByRepositoryPythonPackageSpecification(val specification: PythonRepositoryPackageSpecification) : PythonPackageInstallRequest(specification.nameWithVersionSpec)
}

@ApiStatus.Internal

fun PythonRepositoryPackageSpecification.toInstallRequest(): PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification {
  return PythonPackageInstallRequest.ByRepositoryPythonPackageSpecification(this)
}

@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  abstract var installedPackages: List<PythonPackage>

  @ApiStatus.Internal
  @Volatile
  var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()
    private set

  abstract val repositoryManager: PythonRepositoryManager

  suspend fun installPackage(
    installRequest: PythonPackageInstallRequest,
    options: List<String> = emptyList(),
    withBackgroundProgress: Boolean,
  ): Result<List<PythonPackage>> = installPackages(listOf(installRequest), options, withBackgroundProgress)

  suspend fun installPackages(
    installRequests: List<PythonPackageInstallRequest>,
    options: List<String> = emptyList(),
    withBackgroundProgress: Boolean,
  ): Result<List<PythonPackage>> {
    return if (withBackgroundProgress)
      installPackagesWithBackgroundProcess(installRequests, options)
    else
      installPackagesSilently(installRequests, options)
  }

  suspend fun updatePackage(specification: PythonRepositoryPackageSpecification): Result<List<PythonPackage>> {
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
      outdatedPackages = emptyMap()
      installedPackages = emptyList()
      return Result.failure(it)
    }
    thisLogger().info("Reload packages: finish")

    installedPackages = packages
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).packagesChanged(sdk)
      syncPublisher(PyPackageManager.PACKAGE_MANAGER_TOPIC).packagesRefreshed(sdk)
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      service<PythonSdkCoroutineService>().cs.launch {
        reloadOutdatedPackages()
      }
    }

    return Result.success(packages)
  }

  fun packageExists(pkg: PythonPackage): Boolean = installedPackages.any { it.name.equals(pkg.name, ignoreCase = true) }

  abstract suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit>
  protected abstract suspend fun updatePackageCommand(specification: PythonRepositoryPackageSpecification): Result<Unit>
  protected abstract suspend fun uninstallPackageCommand(pkg: PythonPackage): Result<Unit>
  protected abstract suspend fun reloadPackagesCommand(): Result<List<PythonPackage>>

  @ApiStatus.Internal
  abstract suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>>

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

  private suspend fun installPackagesWithBackgroundProcess(packages: List<PythonPackageInstallRequest>, options: List<String> = emptyList()): Result<List<PythonPackage>> {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.installing.packages")
    }
    else {
      PyBundle.message("python.packaging.installing.package", packages.first().title)
    }

    return withBackgroundProgress(project = project, progressTitle, cancellable = true) {
      reportSequentialProgress(packages.size) { reporter ->
        packages.forEach { specification ->
          reporter.itemStep(PyBundle.message("python.packaging.installing.package", specification.title))
          runCatching {
            installPackageInternal(specification, options)
          }.onFailure { return@withBackgroundProgress Result.failure(it) }
        }
      }

      refreshPaths()
      reloadPackages()
    }
  }

  private suspend fun installPackagesSilently(
    specifications: List<PythonPackageInstallRequest>,
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


  private suspend fun installPackageInternal(specification: PythonPackageInstallRequest, options: List<String>): Result<Unit> {
    val result = runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.new.project.install.failed.title", specification.title), specification.title) {
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

  @ApiStatus.Internal
  suspend fun reloadOutdatedPackages() {
    if (installedPackages.isEmpty()) {
      outdatedPackages = emptyMap()
      return
    }
    val loadedPackages = loadOutdatedPackagesCommand().getOrElse {
      thisLogger().warn("Failed to load outdated packages", it)
      emptyList()
    }
    val packageMap = loadedPackages.associateBy { it.name }
    outdatedPackages = packageMap
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).outdatedPackagesChanged(sdk)
    }
  }

  fun createPackageSpecificationWithSpec(packageName: String, versionSpec: PyRequirementVersionSpec? = null): PythonRepositoryPackageSpecification? {
    return repositoryManager.findPackageRepository(packageName)?.createPackageSpecificationWithSpec(packageName, versionSpec)
  }

  fun createPackageSpecification(packageName: String, version: String? = null, relation: PyRequirementRelation = PyRequirementRelation.EQ): PythonRepositoryPackageSpecification? {
    return repositoryManager.findPackageRepository(packageName)?.createPackageSpecification(packageName, version, relation)
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