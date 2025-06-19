// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "removal")

package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.util.messages.Topic
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PythonDependenciesExtractor
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.dependencies.PythonDependenciesManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec
import com.jetbrains.python.sdk.PythonSdkCoroutineService
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.util.concurrent.CancellationException


/**
 * Represents a Python package manager for a specific Python SDK. Encapsulate main operations with package managers
 * @see com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI to execute commands with UI handlers
 */
@ApiStatus.Experimental
abstract class PythonPackageManager(val project: Project, val sdk: Sdk) {
  private val lazyInitialization = service<PythonSdkCoroutineService>().cs.launch(start = CoroutineStart.LAZY) {
    try {
      repositoryManager.initCaches()
      reloadPackages()
      Unit
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (t: Throwable) {
      thisLogger().error("Failed to initialize PythonPackageManager for $sdk", t)
    }
  }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  protected open var dependencies: List<PythonPackage> = emptyList()

  @ApiStatus.Internal
  @Volatile
  protected open var installedPackages: List<PythonPackage> = emptyList()

  @ApiStatus.Internal
  @Volatile
  protected var outdatedPackages: Map<String, PythonOutdatedPackage> = emptyMap()

  abstract val repositoryManager: PythonRepositoryManager

  @ApiStatus.Internal
  open fun getDependencyManager(): PythonDependenciesManager? {
    return null
  }

  @ApiStatus.Internal
  fun findPackageSpecificationWithVersionSpec(
    packageName: String,
    versionSpec: PyRequirementVersionSpec? = null,
  ): PythonRepositoryPackageSpecification? {
    return repositoryManager.repositories.firstNotNullOfOrNull {
      it.findPackageSpecificationWithSpec(packageName, versionSpec)
    }
  }

  @ApiStatus.Internal
  suspend fun sync(): PyResult<List<PythonPackage>> {
    syncCommand().getOr { return it }
    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun installPackage(installRequest: PythonPackageInstallRequest, options: List<String> = emptyList()): PyResult<List<PythonPackage>> {
    waitForInit()
    installPackageCommand(installRequest, options).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun updatePackages(vararg packages: PythonRepositoryPackageSpecification): PyResult<List<PythonPackage>> {
    waitForInit()
    updatePackageCommand(*packages).getOr { return it }

    return reloadPackages()
  }

  @ApiStatus.Internal
  suspend fun uninstallPackage(vararg packages: String): PyResult<List<PythonPackage>> {
    if (packages.isEmpty()) {
      return PyResult.success(installedPackages)
    }

    waitForInit()
    reloadDependencies()

    val normalizedPackagesNames = packages.map { normalizePackageName(it) }
    uninstallPackageCommand(*normalizedPackagesNames.toTypedArray()).getOr { return it }
    return reloadPackages()
  }

  @ApiStatus.Internal
  open suspend fun reloadPackages(): PyResult<List<PythonPackage>> {
    val packages = loadPackagesCommand().getOr {
      outdatedPackages = emptyMap()
      installedPackages = emptyList()
      return it
    }
    if (packages == installedPackages)
      return PyResult.success(packages)

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

    return PyResult.success(packages)
  }

  @ApiStatus.Internal
  suspend fun listInstalledPackages(): List<PythonPackage> {
    waitForInit()
    return listInstalledPackagesSnapshot()
  }

  @ApiStatus.Internal
  fun listInstalledPackagesSnapshot(): List<PythonPackage> {
    return installedPackages
  }

  @ApiStatus.Internal
  suspend fun listOutdatedPackages(): Map<String, PythonOutdatedPackage> {
    waitForInit()
    return listOutdatedPackagesSnapshot()
  }


  @ApiStatus.Internal
  fun listOutdatedPackagesSnapshot(): Map<String, PythonOutdatedPackage> {
    return outdatedPackages
  }

  private suspend fun reloadOutdatedPackages() {
    if (installedPackages.isEmpty()) {
      outdatedPackages = emptyMap()
      return
    }
    val loadedPackages = loadOutdatedPackagesCommand().onFailure {
      thisLogger().warn("Failed to load outdated packages $it")
    }.getOrNull() ?: emptyList()

    val packageMap = loadedPackages.associateBy { it.name }
    if (outdatedPackages == packageMap)
      return

    outdatedPackages = packageMap
    ApplicationManager.getApplication().messageBus.apply {
      syncPublisher(PACKAGE_MANAGEMENT_TOPIC).outdatedPackagesChanged(sdk)
    }
  }


  @ApiStatus.Internal
  suspend fun waitForInit() {
    lazyInitialization.join()
  }

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun syncCommand(): PyResult<Unit>


  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun installPackageCommand(installRequest: PythonPackageInstallRequest, options: List<String>): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit>

  @ApiStatus.Internal
  @CheckReturnValue
  protected abstract suspend fun uninstallPackageCommand(vararg pythonPackages: String): PyResult<Unit>

  @ApiStatus.Internal
  protected abstract suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>>

  @ApiStatus.Internal
  protected abstract suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>>

  @ApiStatus.Internal
  suspend fun reloadDependencies(): List<PythonPackage> {
    val dependenciesExtractor = PythonDependenciesExtractor.forSdk(sdk) ?: return emptyList()
    val targetModule = project.modules.find { it.pythonSdk == sdk } ?: return emptyList()
    dependencies = dependenciesExtractor.extract(targetModule)
    return dependencies
  }

  @ApiStatus.Internal
  fun listDependencies(): List<PythonPackage> = dependencies

  companion object {
    fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
      val pythonPackageManagerService = project.service<PythonPackageManagerService>()
      val manager = pythonPackageManagerService.forSdk(project, sdk)
      //We need to call the lazy load if not inited
      manager.lazyInitialization.start()
      return manager
    }

    @Topic.AppLevel
    val PACKAGE_MANAGEMENT_TOPIC: Topic<PythonPackageManagementListener> = Topic(PythonPackageManagementListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
    val RUNNING_PACKAGING_TASKS: Key<Boolean> = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks")
  }
}