// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools.sdkTools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.requirements.PyPackageVersion
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gives a mock SDK of a test a package manager with no packages, which runs no process: a mock SDK cannot run, see
 * [PythonMockSdkKeys]. A project with a package manager of its own for a mock, which a test fills with packages, gets
 * none from here, see [yieldTo]. [PythonMockSdk] registers it with the first mock SDK, see [registerOnce].
 */
@TestOnly
class PythonMockPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? {
    if (project.getUserData(OWN_MANAGER) == true) return null
    @Suppress("DEPRECATION") // The keys of a mock are user data of its SDK.
    return if (PythonMockSdkKeys.isMock(interpreter.getSdkAPI())) PythonMockPackageManager(project, interpreter) else null
  }

  companion object {
    private val registered = AtomicBoolean()
    private val OWN_MANAGER: Key<Boolean> = Key.create("python.mock.own.package.manager")

    /** Makes [project] get no manager from here: a provider of the project gives the managers of its mocks. */
    @JvmStatic
    fun yieldTo(project: Project) {
      project.putUserData(OWN_MANAGER, true)
    }

    /** Registers this provider for the rest of the test run, before the providers of the plugin, once. */
    @JvmStatic
    fun registerOnce() {
      if (!registered.compareAndSet(false, true)) return
      PythonPackageManagerProvider.EP_NAME.point.registerExtension(
        PythonMockPackageManagerProvider(), LoadingOrder.FIRST, ApplicationManager.getApplication())
    }
  }
}

/** A package manager with no packages that runs no process, see [PythonMockPackageManagerProvider]. */
@TestOnly
private class PythonMockPackageManager(project: Project, interpreter: PythonInterpreter) : PythonPackageManager(project, interpreter) {
  override val repositoryManager: PythonRepositoryManager = EmptyRepositoryManager(project)
  override val dependenciesFilesRelativePaths: List<Path> = emptyList()

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = PyResult.success(emptyList())
  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> = PyResult.success(emptyList())
  override suspend fun syncLockedCommand(): PyResult<Unit> = PyResult.success(Unit)
  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = PyResult.success(Unit)

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> =
    PyResult.success(Unit)

  override suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = PyResult.success(Unit)
}

/** A repository manager with no repositories, see [PythonMockPackageManager]. */
@TestOnly
private class EmptyRepositoryManager(override val project: Project) : PythonRepositoryManager {
  override val allRepositories: List<PyPackageRepository> = emptyList()

  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> =
    PyResult.localizedError("No repository for $packageName in a test")

  override suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion? = null
  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? = null
  override suspend fun refreshCaches(): Result<Unit, PythonRepositoryManager.PythonRepositoryIOError> = Result.success(Unit)
  override suspend fun initCaches(): Result<Unit, PythonRepositoryManager.PythonRepositoryIOError> = Result.success(Unit)
  override suspend fun findPackageSpecification(requirement: PyRequirement, repository: PyPackageRepository?): PythonRepositoryPackageSpecification? = null
}
