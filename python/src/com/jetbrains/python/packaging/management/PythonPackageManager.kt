// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.messages.Topic
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PythonDependenciesExtractor
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls


@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  private val lazyInitialization by lazy {
    service<PythonSdkCoroutineService>().cs.launch {
      repositoryManager.initCaches()
      reloadPackages()
    }.asCompletableFuture()
  }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  protected open var dependencies: List<PythonPackage> = emptyList()

  @Volatile
  open var installedPackages: List<PythonPackage> = emptyList()
    protected set

  @ApiStatus.Internal
  @Volatile
  var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()
    private set

  abstract val repositoryManager: PythonRepositoryManager


  fun isPackageInstalled(pkg: PythonPackage): Boolean = installedPackages.any { it.name == pkg.name }

  fun findPackageSpecificationWithVersionSpec(
    packageName: String,
    versionSpec: PyRequirementVersionSpec? = null,
  ): PythonRepositoryPackageSpecification? {
    return repositoryManager.repositories.firstNotNullOfOrNull {
      it.findPackageSpecificationWithSpec(packageName, versionSpec)
    }
  }

  fun findPackageSpecification(
    packageName: String,
    version: String? = null,
    relation: PyRequirementRelation = PyRequirementRelation.EQ,
  ): PythonRepositoryPackageSpecification? {
    val versionSpec = version?.let { pyRequirementVersionSpec(relation, version) }
    return findPackageSpecificationWithVersionSpec(packageName, versionSpec)
  }

  suspend fun installPackage(installRequest: PythonPackageInstallRequest, options: List<String> = emptyList()): Result<List<PythonPackage>> {
    val progressTitle = when (installRequest) {
      is PythonPackageInstallRequest.AllRequirements -> PyBundle.message("python.packaging.installing.requirements")
      is PythonPackageInstallRequest.ByLocation -> PyBundle.message("python.packaging.installing.package", installRequest.title)
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> if (installRequest.specifications.size == 1) {
        PyBundle.message("python.packaging.installing.package", installRequest.specifications.first().name)
      }
      else {
        PyBundle.message("python.packaging.installing.packages")
      }
    }

    executeCommand(progressTitle) {
      waitForInit()
      installPackageCommand(installRequest, options)
    }.onFailure {
      return Result.failure(it)
    }


    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun updatePackages(vararg packages: PythonRepositoryPackageSpecification): Result<List<PythonPackage>> {
    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.updating.packages")
    }
    else {
      PyBundle.message("python.packaging.updating.package", packages.first().name)
    }

    executeCommand(progressTitle) {
      waitForInit()
      updatePackageCommand(*packages)
    }.onFailure {
      return Result.failure(it)
    }

    return reloadPackages()
  }


  suspend fun uninstallPackage(vararg packages: String): Result<List<PythonPackage>> {
    if (packages.isEmpty()) {
      return Result.success(installedPackages)
    }

    val progressTitle = if (packages.size > 1) {
      PyBundle.message("python.packaging.uninstall.packages")
    }
    else {
      PyBundle.message("python.packaging.uninstall.package", packages.first())
    }

    executeCommand(progressTitle) {
      waitForInit()
      uninstallPackageCommand(*packages)
    }.onFailure {
      return Result.failure(it)
    }

    return reloadPackages()
  }

  open suspend fun reloadPackages(): Result<List<PythonPackage>> {
    val progressTitle = PyBundle.message("python.toolwindow.packages.update.packages")
    val packages = executeCommand(progressTitle) {
      loadPackagesCommand()
    }.getOrElse {
      outdatedPackages = emptyMap()
      installedPackages = emptyList()
      return Result.failure(it)
    }
    if (packages == installedPackages)
      return Result.success(packages)

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

    refreshPaths()
    return Result.success(packages)
  }

  private suspend fun reloadOutdatedPackages() {
    if (installedPackages.isEmpty()) {
      outdatedPackages = emptyMap()
      return
    }
    val loadedPackages = loadOutdatedPackagesCommand().getOrElse {
      thisLogger().error("Failed to load outdated packages", it)
      emptyList()
    }

    val packageMap = loadedPackages.associateBy { it.name }
    if (outdatedPackages == packageMap)
      return

    outdatedPackages = packageMap
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).outdatedPackagesChanged(sdk)
    }
  }


  private suspend fun refreshPaths() = edtWriteAction {
    // Background refreshing breaks structured concurrency: there is a some activity in background that locks files.
    // Temporary folders can't be deleted on Windows due to that.
    // That breaks tests.
    // This code should be deleted, but disabled temporary to fix tests
    if (!(ApplicationManager.getApplication().isUnitTestMode && SystemInfoRt.isWindows)) {
      VfsUtil.markDirtyAndRefresh(true, true, true, *sdk.rootProvider.getFiles(OrderRootType.CLASSES))
    }
    PythonSdkUpdater.scheduleUpdate(sdk, project)
  }

  private suspend fun <T> executeCommand(
    progressTitle: @Nls String,
    operation: suspend (() -> Result<T>),
  ): Result<T> = PythonPackageManagerUIHelpers.runPackagingOperationBackground(project, progressTitle) {
    operation()
  }

  suspend fun waitForInit() {
    lazyInitialization.await()
  }


  @ApiStatus.Internal
  protected abstract suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): Result<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): Result<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun uninstallPackageCommand(vararg pythonPackages: String): Result<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun loadPackagesCommand(): Result<List<PythonPackage>>

  @ApiStatus.Internal
  protected abstract suspend fun loadOutdatedPackagesCommand(): Result<List<PythonOutdatedPackage>>

  @ApiStatus.Internal
  suspend fun reloadDependencies(): List<PythonPackage> {
    val dependenciesExtractor = PythonDependenciesExtractor.forSdk(sdk) ?: return emptyList()
    val targetModule = project.modules.find { it.pythonSdk == sdk } ?: return emptyList()
    dependencies = dependenciesExtractor.extract(targetModule)
    return dependencies
  }

  fun listDependencies(): List<PythonPackage> = dependencies

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      val pythonPackageManagerService = project.service<PythonPackageManagerService>()
      val manager = pythonPackageManagerService.forSdk(project, sdk)
      //We need to call the lazy load if not inited
      manager.lazyInitialization
      return manager
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC: Topic<PythonPackageManagementListener> = Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
    val RUNNING_PACKAGING_TASKS: Key<Boolean> = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks")
  }
}